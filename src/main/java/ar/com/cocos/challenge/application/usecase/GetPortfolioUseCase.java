package ar.com.cocos.challenge.application.usecase;

import ar.com.cocos.challenge.domain.model.Portfolio;
import ar.com.cocos.challenge.domain.port.in.GetPortfolioPort;
import ar.com.cocos.challenge.domain.service.PortfolioCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Implementación del caso de uso: Consultar Portfolio.
 */
@Service
@RequiredArgsConstructor
public class GetPortfolioUseCase implements GetPortfolioPort {

    private final PortfolioCalculator portfolioCalculator;

    @Override
    @Transactional(readOnly = true)
    public Portfolio execute(Long userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        return portfolioCalculator.calculate(userId);
    }
}