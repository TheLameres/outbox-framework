package io.coffeeshop.core.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * Денежная сумма: величина вместе с валютой.
 *
 * <p>Валюта — часть значения, а не аннотация: сумма без валюты не является деньгами.
 * Масштаб нормализуется до двух знаков в компактном конструкторе, поэтому
 * {@code new Money(new BigDecimal("3.5"), "eur")} и {@code Money.of("3.50")} равны
 * и по {@code equals}, и в JSON.
 */
public record Money(BigDecimal amount, String currency) {

    public static final String DEFAULT_CURRENCY = "EUR";

    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be >= 0, got " + amount);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        Objects.requireNonNull(currency, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException(
                    "currency must be an ISO-4217 code of 3 letters, got " + currency);
        }
        currency = currency.toUpperCase(Locale.ROOT);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    public static Money of(String amount) {
        return of(new BigDecimal(amount));
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be >= 0, got " + quantity);
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
