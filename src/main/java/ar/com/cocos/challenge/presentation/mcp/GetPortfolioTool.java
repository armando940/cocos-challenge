package ar.com.cocos.challenge.presentation.mcp;

import ar.com.cocos.challenge.domain.model.Portfolio;
import ar.com.cocos.challenge.domain.port.in.GetPortfolioPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MCP Tool para consultar portfolio de un usuario.
 * COMENTADO: Requiere spring-ai-tool dependency que no está en mavenCentral estable.
 */
// @Component
// @RequiredArgsConstructor
public class GetPortfolioTool {

    // private final GetPortfolioPort getPortfolioPort;

    // @Tool(name = "get_portfolio", description = "Obtiene el portfolio completo de un usuario: valor total, efectivo disponible y posiciones")
    // public Portfolio getPortfolio(
    //         @ToolParam(description = "ID del usuario") Long userId
    // ) {
    //     return getPortfolioPort.execute(userId);
    // }
}