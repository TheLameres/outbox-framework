package io.coffeeshop.core.event;

import io.coffeeshop.core.domain.IngredientRequirement;
import io.coffeeshop.core.domain.Money;
import io.coffeeshop.core.domain.Unit;
import io.txbox.core.event.DomainEvent;
import io.txbox.core.event.DomainEventScanner;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контрактные тесты событий.
 *
 * <p>Проверяют ровно то, от чего зависит работа фреймворка в рантайме:
 * <ul>
 *   <li>сканер находит все события по тем же {@code eventType}, что уедут в Kafka;</li>
 *   <li>имена не коллизируют: реестр сканера — плоская карта {@code eventType -> Class};</li>
 *   <li>payload переживает JSON-раундтрип (это то, что делает consumer при десериализации).</li>
 * </ul>
 */
class EventContractTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T09:00:00Z");

    // ── реестр сканера ────────────────────────────────────────────────────────

    @Test
    void scannerFindsEveryEventUnderExpectedKey() {
        Map<String, Class<?>> registry = new DomainEventScanner(
                List.of("io.coffeeshop.core.event"),
                getClass().getClassLoader()).scan();

        assertThat(registry).containsKeys(
                "order.placed",
                "order.cancelled",
                "order.completed",
                "inventory.reserved",
                "inventory.rejected",
                "inventory.released",
                "barista.drink-prepared",
                "barista.drink-failed");

        assertThat(registry.get("order.placed")).isEqualTo(OrderEvents.OrderPlaced.class);
        assertThat(registry.get("barista.drink-failed"))
                .isEqualTo(BaristaEvents.DrinkPreparationFailed.class);
    }

    @Test
    void everyEventDeclaresExplicitEventType() {
        // Явное имя вместо getSimpleName(): переименование record не ломает совместимость,
        // а вложенные record'ы вроде Line/Item/Prepared не попадают в реестр.
        var scannerRegistry = new DomainEventScanner(
                List.of("io.coffeeshop.core.event"),
                getClass().getClassLoader()).scan();

        assertThat(scannerRegistry.values()).allSatisfy(type -> {
            DomainEvent annotation = type.getAnnotation(DomainEvent.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.eventType()).isNotBlank();
            assertThat(annotation.eventType()).contains(".");
        });

        // Сканер регистрирует только сами события, не вложенные value-объекты
        assertThat(scannerRegistry).hasSize(8);
    }

    // ── JSON round-trip ───────────────────────────────────────────────────────

    @Test
    void orderPlacedSurvivesRoundTripWithRequirements() {
        var event = new OrderEvents.OrderPlaced(
                ORDER_ID,
                List.of(new OrderEvents.OrderPlaced.Line(
                        UUID.randomUUID(),
                        "Flat White",
                        2,
                        Money.of("3.50"),
                        List.of(new IngredientRequirement("BEANS_ARABICA",
                                new BigDecimal("18"), Unit.GRAM)))),
                Money.of("7.00"),
                NOW);

        var json = MAPPER.writeValueAsString(event);
        var restored = MAPPER.readValue(json, OrderEvents.OrderPlaced.class);

        assertThat(restored.orderId()).isEqualTo(ORDER_ID);
        assertThat(restored.lines()).hasSize(1);
        assertThat(restored.lines().getFirst().requirements())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.ingredientCode()).isEqualTo("BEANS_ARABICA");
                    assertThat(r.amount()).isEqualByComparingTo("18");
                    assertThat(r.unit()).isEqualTo(Unit.GRAM);
                });
        assertThat(restored.total()).isEqualTo(Money.of("7.00"));
    }

    @Test
    void orderPlacedLineScalesRequirementsByQuantity() {
        var line = new OrderEvents.OrderPlaced.Line(
                UUID.randomUUID(), "Latte", 3, Money.of("4.00"),
                List.of(new IngredientRequirement("MILK", new BigDecimal("150"), Unit.MILLILITER)));

        assertThat(line.totalRequirements())
                .singleElement()
                .satisfies(r -> assertThat(r.amount()).isEqualByComparingTo("450"));
    }

    @Test
    void ingredientsRejectedCarriesMissingAmounts() {
        var event = new InventoryEvents.IngredientsRejected(
                ORDER_ID,
                List.of(new InventoryEvents.IngredientsRejected.Missing(
                        "MILK", new BigDecimal("300"), new BigDecimal("50"), Unit.MILLILITER)),
                NOW);

        var restored = MAPPER.readValue(
                MAPPER.writeValueAsString(event), InventoryEvents.IngredientsRejected.class);

        assertThat(restored.missing()).singleElement().satisfies(m -> {
            assertThat(m.ingredientCode()).isEqualTo("MILK");
            assertThat(m.requested()).isEqualByComparingTo("300");
            assertThat(m.available()).isEqualByComparingTo("50");
        });
    }

    @Test
    void cancellationReasonSurvivesRoundTrip() {
        var event = new OrderEvents.OrderCancelled(
                ORDER_ID, OrderEvents.CancellationReason.OUT_OF_STOCK, "no milk", NOW);

        var restored = MAPPER.readValue(
                MAPPER.writeValueAsString(event), OrderEvents.OrderCancelled.class);

        assertThat(restored.reason()).isEqualTo(OrderEvents.CancellationReason.OUT_OF_STOCK);
    }

    @Test
    void baristaEventsSurviveRoundTrip() {
        var prepared = new BaristaEvents.DrinkPrepared(ORDER_ID, "T-17", NOW);
        var failed = new BaristaEvents.DrinkPreparationFailed(ORDER_ID, "grinder jam", NOW);

        assertThat(MAPPER.readValue(MAPPER.writeValueAsString(prepared),
                BaristaEvents.DrinkPrepared.class).ticketNo()).isEqualTo("T-17");
        assertThat(MAPPER.readValue(MAPPER.writeValueAsString(failed),
                BaristaEvents.DrinkPreparationFailed.class).reason()).isEqualTo("grinder jam");
    }

    @Test
    void headersAreStableAggregateNames() {
        // Значения попадают в txbox-aggregate-type и используются в @InboxEventListener(source = ...)
        assertThat(OrderEvents.AGGREGATE_TYPE).isEqualTo("Order");
        assertThat(InventoryEvents.AGGREGATE_TYPE).isEqualTo("Inventory");
        assertThat(BaristaEvents.AGGREGATE_TYPE).isEqualTo("Barista");
    }

    @Test
    void emptyOrderPayloadIsRejectedAtConstruction() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                new OrderEvents.OrderPlaced(ORDER_ID, List.of(), Money.of("0.00"), NOW)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
