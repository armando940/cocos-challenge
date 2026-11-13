package ar.com.cocos.challenge.infrastructure.adapter.in.web;

import ar.com.cocos.challenge.application.usecase.SearchInstrumentsUseCase;
import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.MarketPrice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para búsqueda de instrumentos.
 */
@RestController
@RequestMapping("/api/v1/instruments")
@Tag(name = "Instrumentos", description = "Búsqueda de activos financieros")
@RequiredArgsConstructor
public class InstrumentsController {

    private final SearchInstrumentsUseCase searchInstrumentsUseCase;

    @GetMapping("/search")
    @Operation(summary = "Buscar instrumentos por ticker o nombre")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resultados de búsqueda",
            content = @Content(schema = @Schema(implementation = SearchResponse.class)))
    })
    @Cacheable(value = "instrument-search", key = "#query + ':' + #page + ':' + #size")
    public ResponseEntity<SearchResponse> search(
            @Parameter(description = "Texto de búsqueda (ticker o nombre)", example = "GGAL")
            @RequestParam(name = "q") String query,
            @Parameter(description = "Página (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        SearchInstrumentsUseCase.SearchResult result = searchInstrumentsUseCase.execute(query, page, size);
        return ResponseEntity.ok(SearchResponse.from(result));
    }

    // DTOs
    public record SearchResponse(
            List<InstrumentResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static SearchResponse from(SearchInstrumentsUseCase.SearchResult result) {
            return new SearchResponse(
                    result.content().stream().map(InstrumentResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }

    public record InstrumentResponse(
            Long instrumentId,
            String ticker,
            String name,
            String type,
            String currentPrice,
            String previousClose,
            String dailyChangePct
    ) {
        public static InstrumentResponse from(SearchInstrumentsUseCase.InstrumentWithPrice iwp) {
            Instrument inst = iwp.instrument();
            MarketPrice price = iwp.marketPrice();
            return new InstrumentResponse(
                    inst.id(),
                    inst.ticker(),
                    inst.name(),
                    inst.type().name(),
                    price != null ? price.close().toPlainString() : "0",
                    price != null ? price.previousClose().toPlainString() : "0",
                    price != null ? price.dailyChangePct().toPlainString() : "0"
            );
        }
    }
}