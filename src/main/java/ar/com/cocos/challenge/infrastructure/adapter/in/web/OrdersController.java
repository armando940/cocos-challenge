package ar.com.cocos.challenge.infrastructure.adapter.in.web;

import ar.com.cocos.challenge.application.usecase.CancelOrderUseCase;
import ar.com.cocos.challenge.application.usecase.PlaceOrderUseCase;
import ar.com.cocos.challenge.domain.model.Money;
import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.model.OrderType;
import ar.com.cocos.challenge.domain.port.in.CancelOrderPort;
import ar.com.cocos.challenge.domain.port.in.PlaceOrderPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Controlador REST para gestión de órdenes.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Órdenes", description = "Envío y gestión de órdenes de trading")
@RequiredArgsConstructor
public class OrdersController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    @PostMapping
    @Operation(summary = "Enviar una orden al mercado")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Orden creada/ejecutada",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Orden rechazada por validación",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "409", description = "Conflicto de idempotencia",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderPort.Command command = new PlaceOrderPort.Command(
                request.userId(),
                request.instrumentId(),
                request.side(),
                request.type(),
                request.price() != null ? Money.of(request.price()) : null,
                request.size(),
                request.amount() != null ? Money.of(request.amount()) : null,
                request.idempotencyKey()
        );

        PlaceOrderPort.Result result = placeOrderUseCase.execute(command);

        HttpStatus status = result.status() == OrderStatus.REJECTED ? HttpStatus.BAD_REQUEST : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(OrderResponse.from(result));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancelar una orden pendiente (solo estado NEW)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Orden cancelada",
            content = @Content(schema = @Schema(implementation = CancelResponse.class))),
        @ApiResponse(responseCode = "404", description = "Orden no encontrada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "La orden no puede cancelarse (no está en NEW)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CancelResponse> cancelOrder(
            @Parameter(description = "ID de la orden") @PathVariable Long orderId) {

        CancelOrderPort.Result result = cancelOrderUseCase.execute(orderId);
        return ResponseEntity.ok(CancelResponse.from(result));
    }

    // DTOs Request
    public record PlaceOrderRequest(
            @NotNull Long userId,
            @NotNull Long instrumentId,
            @NotNull OrderSide side,
            @NotNull OrderType type,
            BigDecimal price,           // obligatorio si LIMIT
            Long size,                  // XOR amount
            BigDecimal amount,          // XOR size
            @NotBlank String idempotencyKey
    ) {}

    // DTOs Response
    public record OrderResponse(
            Long orderId,
            String status,
            String executedPrice,
            Long executedSize,
            String rejectionReason,
            Instant datetime
    ) {
        public static OrderResponse from(PlaceOrderPort.Result result) {
            return new OrderResponse(
                    result.orderId(),
                    result.status().name(),
                    result.executedPrice() != null ? result.executedPrice().toString() : null,
                    result.executedSize(),
                    result.rejectionReason(),
                    result.datetime()
            );
        }
    }

    public record CancelResponse(
            Long orderId,
            String status
    ) {
        public static CancelResponse from(CancelOrderPort.Result result) {
            return new CancelResponse(result.orderId(), result.status().name());
        }
    }

    public record ErrorResponse(String error, String message) {}
}