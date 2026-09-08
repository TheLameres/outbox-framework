package io.txbox.core.store;

import java.time.Instant;

/**
 * Агрегированная статистика очереди сообщений.
 * Используется в health-check и метриках.
 */
public record MessageStats(
        long pending,
        long inFlight,
        long received,
        long processed,
        long failed,
        Instant oldestUnprocessed  // null если очередь пуста
) {
    public static MessageStats empty() {
        return new MessageStats(0, 0, 0, 0, 0, null);
    }
}
