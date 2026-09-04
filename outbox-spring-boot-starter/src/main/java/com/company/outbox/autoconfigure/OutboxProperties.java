package com.company.outbox.autoconfigure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * Конфигурация как record. С Spring Boot 3+ constructor binding для records
 * включается автоматически — {@code @ConstructorBinding} не нужен,
 * а Lombok-геттеров/сеттеров больше не требуется вообще.
 *
 * <p>Дефолты задаются через {@code @DefaultValue}, а НЕ инициализаторами полей.
 */
@Validated
@ConfigurationProperties("outbox")
public record OutboxProperties(

        /** Полностью выключить фреймворк (например, на read-only инстансах). */
        @DefaultValue("true") boolean enabled,

        @DefaultValue Polling polling,
        @DefaultValue Retry retry,
        @DefaultValue Cleanup cleanup,
        @DefaultValue Kafka kafka
) {

    public record Polling(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("1s") Duration interval,
            @DefaultValue("100") @Min(1) @Max(1000) int batchSize,
            /** Публиковать батч параллельно на виртуальных потоках (Java 21). */
            @DefaultValue("true") boolean virtualThreads,
            /** Ограничитель одновременных публикаций — защита продюсера от перегрузки. */
            @DefaultValue("64") @Min(1) int concurrency,
            /** Через сколько вернуть зависшие IN_FLIGHT обратно в PENDING. */
            @DefaultValue("5m") Duration inFlightTimeout
    ) {}

    public record Retry(
            @DefaultValue("5") @Min(1) int maxAttempts,
            @DefaultValue("PT10S") Duration sendTimeout
    ) {}

    public record Cleanup(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("7d") Duration retention,
            @DefaultValue("1h") Duration interval,
            @DefaultValue("10000") int batchSize
    ) {}

    public record Kafka(
            /** Стратегия: SUFFIX | FIXED | BY_EVENT_TYPE */
            @DefaultValue("SUFFIX") Routing routing,
            @DefaultValue("-events") String topicSuffix,
            @DefaultValue("outbox-events") String fixedTopic,
            @DefaultValue Map<String, String> topicByEventType
    ) {
        public enum Routing { SUFFIX, FIXED, BY_EVENT_TYPE }
    }
}
