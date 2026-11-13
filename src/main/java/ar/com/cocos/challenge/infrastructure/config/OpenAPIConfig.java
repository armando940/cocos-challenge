package ar.com.cocos.challenge.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de OpenAPI / Swagger.
 */
@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cocos Capital - Backend Challenge API")
                        .version("1.0.0")
                        .description("""
                                API para sistema de trading/brokerage.
                                
                                ## Endpoints principales:
                                - **Portfolio**: Consulta de valor total, efectivo disponible y posiciones
                                - **Instrumentos**: Búsqueda de activos por ticker/nombre
                                - **Órdenes**: Envío de órdenes MARKET/LIMIT, cancelación
                                
                                ## Reglas de negocio clave:
                                - Órdenes MARKET se ejecutan inmediatamente (estado FILLED)
                                - Órdenes LIMIT quedan pendientes (estado NEW)
                                - Validación de saldo para BUY, tenencia para SELL
                                - Idempotencia obligatoria via idempotencyKey
                                - Sin fracciones de acciones (size entero)
                                """)
                        .license(new License().name("Proprietary").url("https://cocos.capital")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}