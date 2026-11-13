package ar.com.cocos.challenge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuración del servidor MCP (Model Context Protocol).
 * Habilitado con profile "mcp" o "docker".
 * COMENTADO: Requiere spring-ai-mcp-server dependency que no está en mavenCentral estable.
 */
@Configuration
@Profile({"mcp", "docker"})
public class MCPConfig {

    // @Bean
    // public Transport mcpTransport() {
    //     return new HttpServerTransport("/mcp");
    // }

    // @Bean
    // public McpServer mcpServer(Transport transport, McpServerFeatures.SyncToolRegistration... tools) {
    //     return McpServer.builder(transport)
    //             .tools(tools)
    //             .build();
    // }
}