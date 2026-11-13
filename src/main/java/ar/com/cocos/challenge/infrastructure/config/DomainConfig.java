package ar.com.cocos.challenge.infrastructure.config;

import ar.com.cocos.challenge.domain.port.out.InstrumentRepositoryPort;
import ar.com.cocos.challenge.domain.port.out.MarketDataProviderPort;
import ar.com.cocos.challenge.domain.port.out.OrderRepositoryPort;
import ar.com.cocos.challenge.domain.service.BalanceValidator;
import ar.com.cocos.challenge.domain.service.OrderMatcher;
import ar.com.cocos.challenge.domain.service.PortfolioCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registro de los servicios de dominio como beans de Spring.
 *
 * El dominio es agnóstico del framework (no usa @Service ni @Component a
 * propósito); el cableado se hace acá, en infraestructura.
 */
@Configuration
public class DomainConfig {

    @Bean
    public PortfolioCalculator portfolioCalculator(
            OrderRepositoryPort orderRepository,
            InstrumentRepositoryPort instrumentRepository,
            MarketDataProviderPort marketDataProvider) {
        return new PortfolioCalculator(orderRepository, instrumentRepository, marketDataProvider);
    }

    @Bean
    public OrderMatcher orderMatcher(MarketDataProviderPort marketDataProvider) {
        return new OrderMatcher(marketDataProvider);
    }

    @Bean
    public BalanceValidator balanceValidator(
            OrderRepositoryPort orderRepository,
            InstrumentRepositoryPort instrumentRepository,
            MarketDataProviderPort marketDataProvider,
            PortfolioCalculator portfolioCalculator) {
        return new BalanceValidator(orderRepository, instrumentRepository, marketDataProvider, portfolioCalculator);
    }
}
