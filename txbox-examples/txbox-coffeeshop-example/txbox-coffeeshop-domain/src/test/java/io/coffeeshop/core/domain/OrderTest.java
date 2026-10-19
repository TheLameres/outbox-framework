package io.coffeeshop.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static OrderLine line(String price, int qty) {
        return new OrderLine(UUID.randomUUID(), "Flat White", qty, Money.of(price));
    }

    private static Order newOrder() {
        return Order.place(UUID.randomUUID(),
                List.of(line("3.50", 2), line("4.25", 1)),
                Instant.parse("2026-01-01T09:00:00Z"));
    }

    @Test
    void totalIsSumOfLines() {
        assertThat(newOrder().total()).isEqualTo(Money.of("11.25"));
    }

    @Test
    void happyPathTransitions() {
        var order = newOrder()
                .confirm()
                .startPreparing()
                .markReady()
                .complete();

        assertThat(order.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.status().isTerminal()).isTrue();
    }

    @Test
    void rejectFromPlacedKeepsReason() {
        var order = newOrder().reject("no beans");

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.failureReason()).isEqualTo("no beans");
    }

    @Test
    void cancelFromPreparingKeepsReason() {
        var order = newOrder().confirm().startPreparing().cancel("guest left");

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.failureReason()).isEqualTo("guest left");
    }

    @Test
    void illegalTransitionIsRejected() {
        var order = newOrder();   // PLACED

        assertThatThrownBy(order::complete)
                .isInstanceOf(OrderStateException.class)
                .hasMessageContaining("PLACED")
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void terminalStatusAllowsNoFurtherTransitions() {
        var completed = newOrder().confirm().startPreparing().markReady().complete();

        assertThatThrownBy(() -> completed.cancel("too late"))
                .isInstanceOf(OrderStateException.class);
    }

    @Test
    void stateViolationIsIllegalStateSoClassifierTreatsItAsFatal() {
        // Классификатор фреймворка маппит IllegalStateException -> ProcessingOutcome.Fatal.
        // Нарушение бизнес-правила не должно ретраиться бесконечно.
        assertThatThrownBy(() -> newOrder().complete())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelRequiresReason() {
        assertThatThrownBy(() -> newOrder().cancel("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    void transitionsDoNotMutateOriginal() {
        var original = newOrder();
        var confirmed = original.confirm();

        assertThat(original.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmed).isNotSameAs(original);
    }

    @Test
    void emptyOrderRejected() {
        assertThatThrownBy(() -> Order.place(UUID.randomUUID(), List.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one line");
    }

    @Test
    void cannotReserveMoreThanAvailableInStatusGraph() {
        // READY -> PREPARING недопустим: назад по саге не ходим
        var ready = newOrder().confirm().startPreparing().markReady();

        assertThat(OrderStatus.READY.canTransitionTo(OrderStatus.PREPARING)).isFalse();
    }
}
