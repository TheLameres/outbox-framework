package io.coffeeshop.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockLevelTest {

    private static StockLevel beans(String onHand) {
        return new StockLevel("BEANS_ARABICA", Unit.GRAM,
                new BigDecimal(onHand), BigDecimal.ZERO);
    }

    @Test
    void reserveReducesAvailabilityButNotOnHand() {
        var stock = beans("1000").reserve(new BigDecimal("200"));

        assertThat(stock.onHand()).isEqualByComparingTo("1000");
        assertThat(stock.reserved()).isEqualByComparingTo("200");
        assertThat(stock.available()).isEqualByComparingTo("800");
    }

    @Test
    void releaseRestoresAvailability() {
        var stock = beans("1000")
                .reserve(new BigDecimal("200"))
                .release(new BigDecimal("200"));

        assertThat(stock.reserved()).isEqualByComparingTo("0");
        assertThat(stock.available()).isEqualByComparingTo("1000");
    }

    @Test
    void consumeWritesOffReservedAmount() {
        var stock = beans("1000")
                .reserve(new BigDecimal("200"))
                .consume(new BigDecimal("200"));

        assertThat(stock.onHand()).isEqualByComparingTo("800");
        assertThat(stock.reserved()).isEqualByComparingTo("0");
        assertThat(stock.available()).isEqualByComparingTo("800");
    }

    @Test
    void reserveMoreThanAvailableFails() {
        assertThatThrownBy(() -> beans("100").reserve(new BigDecimal("150")))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 150")
                .hasMessageContaining("available 100");
    }

    @Test
    void reserveTwiceCannotExceedOnHand() {
        var stock = beans("100").reserve(new BigDecimal("60"));

        assertThatThrownBy(() -> stock.reserve(new BigDecimal("60")))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void cannotReleaseMoreThanReserved() {
        assertThatThrownBy(() -> beans("100").release(BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only 0 reserved");
    }

    @Test
    void cannotReserveNegativeAmount() {
        assertThatThrownBy(() -> beans("100").reserve(new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be > 0");
    }

    @Test
    void invariantsAreGuardedByConstructor() {
        assertThatThrownBy(() -> new StockLevel("MILK", Unit.MILLILITER,
                new BigDecimal("100"), new BigDecimal("200")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed onHand");
    }

    @Test
    void replenishIncreasesOnHand() {
        var stock = beans("100").replenish(new BigDecimal("900"));

        assertThat(stock.onHand()).isEqualByComparingTo("1000");
        assertThat(stock.available()).isEqualByComparingTo("1000");
    }
}
