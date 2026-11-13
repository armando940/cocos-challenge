package ar.com.cocos.challenge.domain.port.in;

import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.MarketPrice;

import java.util.List;

/**
 * Puerto de entrada para buscar instrumentos.
 */
public interface SearchInstrumentsPort {
    /**
     * Busca instrumentos por ticker o nombre (ILIKE).
     * @param query texto de búsqueda
     * @param page página (0-indexed)
     * @param size tamaño de página
     * @return lista de instrumentos con precios actuales
     */
    SearchResult execute(String query, int page, int size);

    record SearchResult(
        List<InstrumentWithPrice> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {}

    record InstrumentWithPrice(
        Instrument instrument,
        MarketPrice marketPrice
    ) {}
}