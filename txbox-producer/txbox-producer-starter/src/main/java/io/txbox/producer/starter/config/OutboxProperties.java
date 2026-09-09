package io.txbox.producer.starter.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * Конфигурация producer-стороны. Prefix: {@code txbox.producer}.
 * Все записи — records с @DefaultValue; Lombok и @ConstructorBinding не нужны.
 */
@Validated
@ConfigurationProperties("txbox.producer")
public record OutboxProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue Polling polling,
        @DefaultValue Retry retry,
        @DefaultValue Maintenance maintenance,
        @DefaultValue Kafka kafka
) {

    public record Polling(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("1s") Duration interval,
            @DefaultValue("100") @Min(1) @Max(1000) int batchSize,
            @DefaultValue("true") boolean virtualThreads,
            @DefaultValue("64") @Min(1) int concurrency,
            @DefaultValue("5m") Duration inFlightTimeout
    ) {
    }

    public record Retry(
            @DefaultValue("5") @Min(1) int maxAttempts,
            @DefaultValue("10s") Duration sendTimeout
    ) {
    }

    public record Maintenance(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("7d") Duration retention,
            @DefaultValue("1h") Duration interval,
            @DefaultValue("10000") int batchSize
    ) {
    }

    public record Kafka(
            @DefaultValue("SUFFIX") Routing routing,
            @DefaultValue("-events") String topicSuffix,
            @DefaultValue("domain.events") String fixedTopic,
            @DefaultValue Map<String, String> topicByEventType
    ) {
        public enum Routing {SUFFIX, FIXED, BY_EVENT_TYPE}
    }
}
