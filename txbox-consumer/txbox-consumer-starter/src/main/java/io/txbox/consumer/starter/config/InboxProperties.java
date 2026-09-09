package io.txbox.consumer.starter.config;

import io.txbox.consumer.kafka.configuration.InboxConfiguration;
import io.txbox.consumer.kafka.configuration.InboxKafkaConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
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
        @Valid @DefaultValue InboxProperties.InboxKafkaProperties kafka,
        @Valid @DefaultValue InboxProperties.InboxJpaProperties jpa
) implements InboxConfiguration {

    public record InboxKafkaProperties(
            @NotBlank(message = "Kafka topic required")
            @DefaultValue("domain.events")
            String topic,

            @NotBlank(message = "Kafka group-id required")
            @DefaultValue("inbox-consumer")
            String groupId,

            @Positive(message = "Concurrency must be > 0")
            @DefaultValue("1")
            int concurrency,

            @Positive(message = "Poll timeout must be > 0")
            @DefaultValue("3000")
            long pollTimeoutMs
    ) implements InboxKafkaConfiguration {
    }

    public record InboxJpaProperties(
            @Positive(message = "Batch size must be > 0")
            @DefaultValue("10")
            int batchSize
    ) {
    }
}
