package ar.com.cocos.challenge.application.usecase;

import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.MarketPrice;
import ar.com.cocos.challenge.domain.port.in.SearchInstrumentsPort;
import ar.com.cocos.challenge.domain.port.out.InstrumentRepositoryPort;
import ar.com.cocos.challenge.domain.port.out.MarketDataProviderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementación del caso de uso: Buscar Instrumentos.
 */
@Service
@RequiredArgsConstructor
public class SearchInstrumentsUseCase implements SearchInstrumentsPort {

    private final InstrumentRepositoryPort instrumentRepository;
    private final MarketDataProviderPort marketDataProvider;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "instrument-search", key = "#query + ':' + #page + ':' + #size")
    public SearchResult execute(String query, int page, int size) {
        List<Instrument> instruments = instrumentRepository.findByTickerOrNameContainingIgnoreCase(query, page, size);
        long totalElements = instrumentRepository.countByTickerOrNameContainingIgnoreCase(query);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        // Obtener precios para todos los instrumentos encontrados
        Set<Long> instrumentIds = instruments.stream()
                .map(Instrument::id)
                .collect(Collectors.toSet());

        Map<Long, MarketPrice> prices = marketDataProvider.findLatestPrices(instrumentIds);

        List<InstrumentWithPrice> content = instruments.stream()
                .map(inst -> new InstrumentWithPrice(inst, prices.get(inst.id())))
                .toList();

        return new SearchResult(content, page, size, totalElements, totalPages);
    }
}