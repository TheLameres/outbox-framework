package io.txbox.producer.starter.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.txbox.core.routing.DestinationResolver;
import io.txbox.producer.api.OutboxPublisher;
import io.txbox.producer.api.OutboxStore;
import io.txbox.producer.jpa.repository.OutboxJpaRepository;
import io.txbox.producer.jpa.store.JpaOutboxStore;
import io.txbox.producer.kafka.KafkaOutboxPublisher;
import io.txbox.producer.starter.health.OutboxHealthIndicator;
import io.txbox.producer.starter.maintenance.OutboxMaintenance;
import io.txbox.producer.starter.metrics.OutboxMetrics;
import io.txbox.producer.starter.polling.OutboxPoller;
import io.txbox.producer.starter.polling.OutboxSchedulerManager;
import io.txbox.producer.starter.template.OutboxTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@EnableConfigurationProperties(OutboxProperties.class)
@EnableJpaRepositories(basePackages = "io.txbox.producer.jpa.repository")
@EntityScan(basePackages = "io.txbox.producer.jpa.entity")
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JpaOutboxStore jpaOutboxStore(OutboxJpaRepository repository) {
        return new JpaOutboxStore(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxTemplate outboxTemplate(OutboxStore store, ObjectMapper objectMapper) {
        return new OutboxTemplate(store, objectMapper);
    }

    @Bean
    OutboxHealthIndicator outboxHealthIndicator(JpaOutboxStore store) {
        return new OutboxHealthIndicator(store);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxPoller outboxPoller(OutboxStore store,
                              OutboxPublisher publisher,
                              OutboxProperties properties) {
        return new OutboxPoller(store, publisher, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxMaintenance outboxMaintenance(JpaOutboxStore store,
                                        OutboxProperties properties) {
        return new OutboxMaintenance(store, properties);
    }

    @Bean
    OutboxSchedulerManager outboxSchedulerManager(TaskScheduler taskScheduler,
                                                  OutboxProperties outboxProperties,
                                                  OutboxPoller outboxPoller,
                                                  OutboxMaintenance outboxMaintenance) {
        return new OutboxSchedulerManager(taskScheduler, outboxProperties, outboxPoller, outboxMaintenance);
    }

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    OutboxMetrics outboxMetrics(JpaOutboxStore store,
                                ObjectProvider<MeterRegistry> meters) {
        return new OutboxMetrics(store, meters.getIfAvailable(SimpleMeterRegistry::new));
    }

    // ── Kafka ─────────────────────────────────────────────────────────────────

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(KafkaTemplate.class)
    static class KafkaConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DestinationResolver outboxDestinationResolver(OutboxProperties properties) {
            var kafka = properties.kafka();
            return switch (kafka.routing()) {
                case SUFFIX -> new DestinationResolver.BySuffix(kafka.topicSuffix());
                case FIXED -> new DestinationResolver.Fixed(kafka.fixedTopic());
                case BY_EVENT_TYPE -> new DestinationResolver.ByEventType(
                        kafka.topicByEventType(), kafka.fixedTopic());
            };
        }

        @Bean
        @ConditionalOnMissingBean
        KafkaOutboxPublisher kafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                  DestinationResolver resolver,
                                                  OutboxProperties properties) {
            return new KafkaOutboxPublisher(
                    kafkaTemplate,
                    resolver,
                    properties.retry().sendTimeout());
        }
    }
}
