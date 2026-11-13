package ar.com.cocos.challenge.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Portfolio completo de un usuario.
 */
public record Portfolio(
    Long userId,
    Money totalAccountValue,    // availableCash + sum(positions.marketValue)
    Money availableCash,        // pesos disponibles para operar (ARS)
    List<Position> positions    // solo instrumentos con quantity > 0
) {
    public Portfolio {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(totalAccountValue, "totalAccountValue cannot be null");
        Objects.requireNonNull(availableCash, "availableCash cannot be null");
        Objects.requireNonNull(positions, "positions cannot be null");
    }

    public Money positionsValue() {
        return positions.stream()
                .map(Position::marketValue)
                .reduce(Money.zero(), Money::add);
    }

    public int positionsCount() {
        return (int) positions.stream().filter(p -> p.quantity() > 0).count();
    }
}