package ar.com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object para representar dinero en pesos argentinos (ARS).
 * Inmutable, usa BigDecimal con scale=2 y RoundingMode.HALF_UP.
 */
public record Money(BigDecimal amount) {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount cannot be null");
    }

    public BigDecimal amount() {
        return amount.setScale(SCALE, ROUNDING);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public Money add(Money other) {
        return new Money(this.amount().add(other.amount()));
    }

    public Money subtract(Money other) {
        return new Money(this.amount().subtract(other.amount()));
    }

    public Money multiply(int factor) {
        return new Money(this.amount().multiply(BigDecimal.valueOf(factor)));
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount().multiply(factor));
    }

    public Money multiply(long factor) {
        return new Money(this.amount().multiply(BigDecimal.valueOf(factor)));
    }

    public Money divide(int divisor) {
        return new Money(this.amount().divide(BigDecimal.valueOf(divisor), SCALE, ROUNDING));
    }

    public boolean isGreaterThan(Money other) {
        return this.amount().compareTo(other.amount()) > 0;
    }

    public boolean isGreaterOrEqual(Money other) {
        return this.amount().compareTo(other.amount()) >= 0;
    }

    public boolean isLessThan(Money other) {
        return this.amount().compareTo(other.amount()) < 0;
    }

    public boolean isZero() {
        return this.amount().compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return this.amount().compareTo(BigDecimal.ZERO) > 0;
    }

    public Money abs() {
        return new Money(this.amount().abs());
    }

    public Money negate() {
        return new Money(this.amount().negate());
    }

    @Override
    public String toString() {
        return amount().toPlainString();
    }
}