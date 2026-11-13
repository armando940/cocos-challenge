package ar.com.cocos.challenge.infrastructure.adapter.in.web;

import ar.com.cocos.challenge.application.usecase.GetPortfolioUseCase;
import ar.com.cocos.challenge.domain.model.Portfolio;
import ar.com.cocos.challenge.domain.model.Position;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para Portfolio.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
@Tag(name = "Portfolio", description = "Consulta de portfolio de usuario")
@RequiredArgsConstructor
public class PortfolioController {

    private final GetPortfolioUseCase getPortfolioUseCase;

    @GetMapping("/{userId}")
    @Operation(summary = "Obtener portfolio de un usuario")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Portfolio encontrado",
            content = @Content(schema = @Schema(implementation = PortfolioResponse.class))),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PortfolioResponse> getPortfolio(
            @Parameter(description = "ID del usuario", example = "1") @PathVariable Long userId) {

        Portfolio portfolio = getPortfolioUseCase.execute(userId);
        return ResponseEntity.ok(PortfolioResponse.from(portfolio));
    }

    // DTOs de respuesta
    public record PortfolioResponse(
            Long userId,
            String totalAccountValue,
            String availableCash,
            List<PositionResponse> positions
    ) {
        public static PortfolioResponse from(Portfolio p) {
            return new PortfolioResponse(
                    p.userId(),
                    p.totalAccountValue().toString(),
                    p.availableCash().toString(),
                    p.positions().stream().map(PositionResponse::from).toList()
            );
        }
    }

    public record PositionResponse(
            Long instrumentId,
            String ticker,
            String name,
            Long quantity,
            String marketValue,
            String totalReturnPct,
            String dailyReturnPct
    ) {
        public static PositionResponse from(Position p) {
            return new PositionResponse(
                    p.instrumentId(),
                    p.ticker(),
                    p.name(),
                    p.quantity(),
                    p.marketValue().toString(),
                    p.totalReturnPct().toPlainString(),
                    p.dailyReturnPct().toPlainString()
            );
        }
    }

    public record ErrorResponse(String error, String message) {}
}