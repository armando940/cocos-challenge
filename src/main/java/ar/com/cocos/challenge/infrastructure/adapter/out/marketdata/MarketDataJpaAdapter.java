package ar.com.cocos.challenge.infrastructure.adapter.out.marketdata;

import ar.com.cocos.challenge.domain.model.MarketPrice;
import ar.com.cocos.challenge.domain.port.out.MarketDataProviderPort;
import ar.com.cocos.challenge.infrastructure.adapter.out.persistence.MarketDataJpaRepository;
import ar.com.cocos.challenge.infrastructure.adapter.out.persistence.PersistenceMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adaptador JPA para MarketDataProviderPort.
 * Incluye Resilience4j: CircuitBreaker, Retry, TimeLimiter.
 */
@Component
@RequiredArgsConstructor
public class MarketDataJpaAdapter implements MarketDataProviderPort {

    private final MarketDataJpaRepository jpaRepository;

    @Override
    @CircuitBreaker(name = "marketData", fallbackMethod = "getLatestPriceFallback")
    @Retry(name = "marketData")
    public Optional<MarketPrice> findLatestPrice(Long instrumentId) {
        return jpaRepository.findFirstByInstrumentIdOrderByDatetimeDesc(instrumentId)
                .map(PersistenceMapper::toDomain);
    }

    @Override
    @CircuitBreaker(name = "marketData", fallbackMethod = "getLatestPricesFallback")
    @Retry(name = "marketData")
    public Map<Long, MarketPrice> findLatestPrices(Set<Long> instrumentIds) {
        return jpaRepository.findByInstrumentIdInOrderByDatetimeDesc(instrumentIds).stream()
                .collect(Collectors.toMap(
                        e -> e.getInstrumentId(),
                        PersistenceMapper::toDomain,
                        (existing, replacement) -> existing // mantener el primero (más reciente)
                ));
    }

    // Fallback methods para CircuitBreaker
    public Optional<MarketPrice> getLatestPriceFallback(Long instrumentId, Exception ex) {
        // En producción: podría consultar cache local, proveedor alternativo, o lanzar excepción
        throw new RuntimeException("Market data service unavailable for instrument " + instrumentId, ex);
    }

    public Map<Long, MarketPrice> getLatestPricesFallback(Set<Long> instrumentIds, Exception ex) {
        throw new RuntimeException("Market data service unavailable for instruments " + instrumentIds, ex);
    }
}