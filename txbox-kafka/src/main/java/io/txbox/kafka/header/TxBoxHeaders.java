package io.txbox.kafka.header;

/**
 * Константы имён Kafka-заголовков фреймворка txbox.
 * Используются и producer (OutboxMessageKafkaConverter),
 * и consumer (InboxMessageKafkaConverter) для единообразной передачи метаданных.
 */
public final class TxBoxHeaders {

    public static final String MESSAGE_ID = "txbox-message-id";
    public static final String EVENT_TYPE = "txbox-event-type";
    public static final String AGGREGATE_TYPE = "txbox-aggregate-type";
    public static final String AGGREGATE_ID = "txbox-aggregate-id";
    public static final String ATTEMPT = "txbox-attempt";
    public static final String OCCURRED_AT = "txbox-occurred-at";
    public static final String CONTENT_TYPE = "content-type";
    public static final String SCHEMA_VERSION = "schema-version";

    private TxBoxHeaders() {
    }
}
