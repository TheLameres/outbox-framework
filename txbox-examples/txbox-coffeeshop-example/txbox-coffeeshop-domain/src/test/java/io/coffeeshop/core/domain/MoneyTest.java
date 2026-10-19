package io.coffeeshop.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalisesScaleAndCurrencyCase() {
        var money = new Money(new BigDecimal("3.5"), "eur");

        assertThat(money.amount()).isEqualByComparingTo("3.50");
        assertThat(money.currency()).isEqualTo("EUR");
        assertThat(money).isEqualTo(Money.of("3.50"));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.of("-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be >= 0");
    }

    @Test
    void rejectsNonIsoCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "EURO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-4217");
    }

    @Test
    void sumsAndMultiplies() {
        var coffee = Money.of("3.50");
        var cake = Money.of("4.25");

        assertThat(coffee.plus(cake)).isEqualTo(Money.of("7.75"));
        assertThat(coffee.times(3)).isEqualTo(Money.of("10.50"));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of("1.00").plus(new Money(BigDecimal.ONE, "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency mismatch");
    }
}
