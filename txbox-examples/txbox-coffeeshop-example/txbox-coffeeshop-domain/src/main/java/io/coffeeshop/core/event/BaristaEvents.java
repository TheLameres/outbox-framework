package io.coffeeshop.core.event;

import io.txbox.core.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * События контекста «Бариста».
 */
public sealed interface BaristaEvents {

    /** Стабильное имя агрегата — заголовок {@code txbox-aggregate-type}. */
    String AGGREGATE_TYPE = "Barista";

    /**
     * Напиток готов. Сервис заказа переводит заказ в {@code READY}.
     */
    @DomainEvent(eventType = "barista.drink-prepared")
    record DrinkPrepared(UUID orderId,
                         String ticketNo,
                         Instant preparedAt) implements BaristaEvents {
    }

    /**
     * Приготовить не удалось (оборудование, ошибка бариста).
     *
     * <p>Сервис заказа реагирует отменой с причиной
     * {@link OrderEvents.CancellationReason#PREPARATION_FAILED}, склад освобождает резерв.
     */
    @DomainEvent(eventType = "barista.drink-failed")
    record DrinkPreparationFailed(UUID orderId,
                                  String reason,
                                  Instant failedAt) implements BaristaEvents {
    }
}
