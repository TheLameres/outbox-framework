package io.txbox.kafka.converter;

import io.txbox.core.model.OutboxMessage;
import io.txbox.kafka.header.TxBoxHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;

/**
 * Конвертер OutboxMessage → ProducerRecord для Kafka.
 * Переносит все txbox-заголовки в Kafka headers.
 * Ключ партиционирования = aggregateId (порядок событий агрегата сохраняется).
 */
public final class OutboxMessageKafkaConverter {

    private OutboxMessageKafkaConverter() {
    }

    public static ProducerRecord<String, String> toProducerRecord(
            OutboxMessage message, String topic) {

        var record = new ProducerRecord<>(topic, message.aggregateId(), message.payload());

        addHeader(record, TxBoxHeaders.MESSAGE_ID, message.messageId().toString());
        addHeader(record, TxBoxHeaders.EVENT_TYPE, message.eventType());
        addHeader(record, TxBoxHeaders.AGGREGATE_TYPE, message.aggregateType());
        addHeader(record, TxBoxHeaders.AGGREGATE_ID, message.aggregateId());
        addHeader(record, TxBoxHeaders.ATTEMPT, Integer.toString(message.attempt()));
        addHeader(record, TxBoxHeaders.OCCURRED_AT, message.timestamp().toString());

        // Пользовательские заголовки (custom routing, priority, etc.)
        message.headers().forEach((key, value) -> addHeader(record, key, value));

        return record;
    }

    private static void addHeader(ProducerRecord<String, String> record,
                                  String key, String value) {
        if (value != null) {
            record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
