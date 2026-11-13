package ar.com.cocos.challenge.unit.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Demuestra el comportamiento del CircuitBreaker con la misma configuración
 * que usa el proyecto para `marketData` (ver application.yml).
 */
class Resilience4jCircuitBreakerTest {

    @Test
    @DisplayName("El circuit breaker se abre al superar el failure rate configurado")
    void opensAfterConfiguredFailureRate() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();

        CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(config)
                .circuitBreaker("marketData");

        Supplier<String> failingCall = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            throw new RuntimeException("market data unavailable");
        });

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(failingCall::get).isInstanceOf(RuntimeException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(5);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(100.0f);
    }
}
