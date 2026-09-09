package io.txbox.kafka.converter;

import io.txbox.core.model.InboxMessage;
import io.txbox.kafka.header.TxBoxHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.UUID;

/**
 * Конвертер ConsumerRecord → InboxMessage.
 *
 * <p>messageId: берётся из заголовка {@code txbox-message-id}; если отсутствует —
 * вычисляется детерминированно как {@code UUID.nameUUIDFromBytes("partition:offset")},
 * что гарантирует идемпотентность дедупликации даже для сообщений без заголовков.
 */
public final class InboxMessageKafkaConverter {

    private InboxMessageKafkaConverter() {
    }

    public static InboxMessage convert(ConsumerRecord<String, String> record) {
        SequencedMap<String, String> headers = extractHeaders(record.headers());

        UUID messageId = Optional.ofNullable(headers.get(TxBoxHeaders.MESSAGE_ID))
                .map(UUID::fromString)
                .orElseGet(() -> UUID.nameUUIDFromBytes(
                        (record.partition() + ":" + record.offset())
                                .getBytes(StandardCharsets.UTF_8)));

        String eventType = headers.getOrDefault(TxBoxHeaders.EVENT_TYPE, "Unknown");

        Instant occurredAt = Optional.ofNullable(headers.get(TxBoxHeaders.OCCURRED_AT))
                .map(Instant::parse)
                .orElse(Instant.now());

        String source = headers.getOrDefault(TxBoxHeaders.AGGREGATE_TYPE, record.topic());
        String sourceId = record.key() != null ? record.key() : "";

        return new InboxMessage(
                messageId,
                source,
                sourceId,
                eventType,
                record.value(),
                headers,
                occurredAt,
                Instant.now(),
                record.partition(),
                record.offset()
        );
    }

    private static SequencedMap<String, String> extractHeaders(Headers kafkaHeaders) {
        var result = new LinkedHashMap<String, String>();
        kafkaHeaders.forEach(h ->
                result.put(h.key(), new String(h.value(), StandardCharsets.UTF_8)));
        return result;
    }
}
