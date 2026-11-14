package ar.com.cocos.challenge.domain.port.out;

import ar.com.cocos.challenge.domain.model.MarketPrice;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Puerto de salida para proveedor de datos de mercado.
 */
public interface MarketDataProviderPort {
    Optional<MarketPrice> findLatestPrice(Long instrumentId);
    Map<Long, MarketPrice> findLatestPrices(Set<Long> instrumentIds);
}