package ar.com.cocos.challenge.domain.port.in;

import ar.com.cocos.challenge.domain.model.Portfolio;

/**
 * Puerto de entrada para consultar portfolio.
 */
public interface GetPortfolioPort {
    Portfolio execute(Long userId);
}