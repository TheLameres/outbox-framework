package io.txbox.consumer.starter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Конфигурация для consumer (inbox) обработки.
 *
 * <p>Пример в application.yml:
 * <pre>
 * txbox:
 *   consumer:
 *     kafka:
 *       topic: "domain.events"
 *       group-id: "my-service-consumer"
 *       concurrency: 3
 *       poll-timeout-ms: 3000
 *     jpa:
 *       batch-size: 100
 * </pre>
 */
@ConfigurationProperties(prefix = "txbox.consumer")
@Validated
public record InboxProperties(
        @Valid KafkaConfig kafka,
        @Valid JpaConfig jpa
) {

    public record KafkaConfig(
            @NotBlank(message = "Kafka topic required")
            String topic,

            @NotBlank(message = "Kafka group-id required")
            String groupId,

            @Positive(message = "Concurrency must be > 0")
            int concurrency,

            @Positive(message = "Poll timeout must be > 0")
            long pollTimeoutMs
    ) {
    }

    public record JpaConfig(
            @Positive(message = "Batch size must be > 0")
            int batchSize
    ) {
    }
}
