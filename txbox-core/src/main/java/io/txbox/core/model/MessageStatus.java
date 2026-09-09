package io.txbox.core.model;

/**
 * Единый enum статусов для обеих сторон: outbox (producer) и inbox (consumer).
 *
 * <p>Статусы, специфичные для одной стороны, задокументированы в Javadoc.
 * {@link #isTerminal()} позволяет без switch определить, завершена ли обработка.
 */
public enum MessageStatus {

    /**
     * Outbox: ожидает публикации в брокер.
     */
    PENDING,

    /**
     * Outbox: захвачен поллером, публикуется прямо сейчас.
     */
    IN_FLIGHT,

    /**
     * Inbox: получено из брокера, ожидает обработки handler-ом.
     */
    RECEIVED,

    /**
     * Успешно обработано (обе стороны).
     */
    PROCESSED,

    /**
     * Исчерпаны попытки — постоянная ошибка (обе стороны).
     */
    FAILED,

    /**
     * Пропущено дедупликацией или фильтром (обе стороны).
     */
    SKIPPED;

    /**
     * Возвращает {@code true} для финальных статусов — дальнейшая обработка не нужна.
     */
    public boolean isTerminal() {
        return switch (this) {
            case PROCESSED, FAILED, SKIPPED -> true;
            case PENDING, IN_FLIGHT, RECEIVED -> false;
        };
    }
}
