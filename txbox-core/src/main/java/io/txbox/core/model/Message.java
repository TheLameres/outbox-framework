package io.txbox.core.model;

import java.time.Instant;
import java.util.SequencedMap;
import java.util.UUID;

/**
 * Корень sealed-иерархии сообщений.
 * Permits: OutboxMessage (исходящий), InboxMessage (входящий).
 * Exhaustive switch по этому интерфейсу не нужен в общем коде,
 * но сама структура закрыта для случайного расширения.
 */
public sealed interface Message permits OutboxMessage, InboxMessage {

    UUID messageId();
    String eventType();
    String payload();
    SequencedMap<String, String> headers();
    Instant timestamp();

    /** Удобный хелпер: получить заголовок по ключу (null если отсутствует). */
    default String header(String key) {
        return headers().get(key);
    }
}
