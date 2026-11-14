package ar.com.cocos.challenge.application.usecase;

import ar.com.cocos.challenge.domain.model.IdempotencyKey;
import ar.com.cocos.challenge.domain.model.IdempotencyConflictException;
import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.InstrumentNotFoundException;
import ar.com.cocos.challenge.domain.model.InsufficientCashException;
import ar.com.cocos.challenge.domain.model.InsufficientSharesException;
import ar.com.cocos.challenge.domain.model.DomainException;
import ar.com.cocos.challenge.domain.model.Money;
import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.model.OrderType;
import ar.com.cocos.challenge.domain.port.in.PlaceOrderPort;
import ar.com.cocos.challenge.domain.port.out.EventPublisherPort;
import ar.com.cocos.challenge.domain.port.out.IdempotencyStorePort;
import ar.com.cocos.challenge.domain.port.out.InstrumentRepositoryPort;
import ar.com.cocos.challenge.domain.port.out.OrderRepositoryPort;
import ar.com.cocos.challenge.domain.port.out.UserRepositoryPort;
import ar.com.cocos.challenge.domain.service.BalanceValidator;
import ar.com.cocos.challenge.domain.service.OrderMatcher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Implementación del caso de uso: Colocar Orden.
 * Incluye idempotencia transaccional y validaciones de negocio.
 *
 * Una orden rechazada por saldo/tenencia NO se descarta: se persiste con
 * estado REJECTED (requisito del challenge) y se devuelve con HTTP 400.
 */
@Service
@RequiredArgsConstructor
public class PlaceOrderUseCase implements PlaceOrderPort {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderUseCase.class);
    private static final long IDEMPOTENCY_TTL_SECONDS = 24 * 3600;

    private final OrderRepositoryPort orderRepository;
    private final InstrumentRepositoryPort instrumentRepository;
    private final UserRepositoryPort userRepositoryPort;
    private final IdempotencyStorePort idempotencyStore;
    private final BalanceValidator balanceValidator;
    private final OrderMatcher orderMatcher;
    private final EventPublisherPort eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Result execute(Command command) {
        Objects.requireNonNull(command, "command cannot be null");

        // 1. Idempotencia: misma key + mismo request => devolver la respuesta original
        var existing = idempotencyStore.findByKey(command.idempotencyKey());
        if (existing.isPresent()) {
            IdempotencyKey key = existing.get();
            if (!key.matchesRequestHash(hashRequest(command))) {
                throw new IdempotencyConflictException(command.idempotencyKey());
            }
            return deserializeResult(key.responseBody());
        }

        // 2. Lock pesimista por usuario: serializa las órdenes del mismo usuario hasta el commit y se toma antes de leer las órdenes FILLED en balanceValidator.validate
        userRepositoryPort.lockById(command.userId());

        // 3. Validar instrumento existe
        Instrument instrument = instrumentRepository.findById(command.instrumentId())
                .orElseThrow(() -> new InstrumentNotFoundException(command.instrumentId()));

        // 4. Crear orden (calcula size si viene amount)
        Order order = orderMatcher.createOrder(command, instrument);

        // 5. Precio de referencia: LIMIT usa el enviado, MARKET el último close
        BigDecimal referencePrice = orderMatcher.resolveExecutionPrice(command);

        // 6. Validar saldo/tenencia. Si no alcanza, la orden se guarda REJECTED.
        try {
            balanceValidator.validate(command.userId(), command.side(), order.size(), referencePrice, instrument);
        } catch (InsufficientCashException | InsufficientSharesException ex) {
            log.info("Orden rechazada userId={} instrumentId={} reason={}",
                    command.userId(), command.instrumentId(), ex.getCode());
            return persistRejected(command, order, ex);
        }

        // 7. Guardar orden inicial (NEW)
        order = orderRepository.save(order);

        // 8. Ejecutar si es MARKET
        if (order.isMarket()) {
            order = executeMarketOrder(order, instrument, referencePrice);
        }

        // 9. Publicar evento de dominio
        publishEvent(order);

        // 10. Guardar respuesta en idempotencia
        Result result = Result.from(order);
        saveIdempotency(command, result);

        return result;
    }

    private Result persistRejected(Command command, Order order, DomainException cause) {
        Order rejected = order.rejected(cause.getCode());
        Order saved = orderRepository.save(rejected);

        eventPublisher.publishOrderRejected(saved);

        Result result = Result.from(saved);
        saveIdempotency(command, result);
        return result;
    }

    private Order executeMarketOrder(Order order, Instrument instrument, BigDecimal referencePrice) {
        Order filledOrder = orderMatcher.executeMarketOrder(order, instrument, referencePrice);
        return orderRepository.save(filledOrder);
    }

    private void publishEvent(Order order) {
        switch (order.status()) {
            case FILLED -> eventPublisher.publishOrderFilled(order);
            case REJECTED -> eventPublisher.publishOrderRejected(order);
            case NEW -> eventPublisher.publishOrderPlaced(order);
            default -> {}
        }
    }

    private void saveIdempotency(Command command, Result result) {
        Instant now = Instant.now();
        idempotencyStore.save(new IdempotencyKey(
                command.idempotencyKey(),
                hashRequest(command),
                serializeResult(result),
                result.status() == OrderStatus.REJECTED ? 400 : 201,
                now,
                now.plusSeconds(IDEMPOTENCY_TTL_SECONDS)
        ));
    }

    private String hashRequest(Command command) {
        try {
            String input = String.format("%d:%d:%s:%s:%s:%s:%s",
                    command.userId(),
                    command.instrumentId(),
                    command.side(),
                    command.type(),
                    command.price() != null ? command.price().amount() : "",
                    command.size() != null ? command.size() : "",
                    command.amount() != null ? command.amount().amount() : ""
            );
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String serializeResult(Result result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la respuesta de la orden", e);
        }
    }

    private Result deserializeResult(String json) {
        try {
            return objectMapper.readValue(json, Result.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo deserializar la respuesta idempotente", e);
        }
    }
}
