package com.company.outbox.autoconfigure;

import com.company.outbox.core.DestinationResolver;
import com.company.outbox.core.OutboxPublisher;
import com.company.outbox.core.OutboxStore;
import com.company.outbox.jpa.JpaOutboxStore;
import com.company.outbox.jpa.OutboxJpaRepository;
import com.company.outbox.kafka.KafkaOutboxPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@EnableConfigurationProperties(OutboxProperties.class)
@EnableJpaRepositories(basePackages = "com.company.outbox.jpa")
@EntityScan(basePackages = "com.company.outbox.jpa")
public class OutboxAutoConfiguration {

    // ---------- хранилище ----------

    @Bean
    @ConditionalOnMissingBean
    OutboxStore jpaOutboxStore(OutboxJpaRepository repository) {
        return new JpaOutboxStore(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxTemplate outboxTemplate(OutboxStore store, ObjectMapper objectMapper) {
        return new OutboxTemplate(store, objectMapper);
    }

    // ---------- Kafka ----------

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(KafkaTemplate.class)
    static class KafkaConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DestinationResolver outboxDestinationResolver(OutboxProperties properties) {
            var kafka = properties.kafka();
            // Exhaustive switch по enum — новый вариант Routing сломает компиляцию
            return switch (kafka.routing()) {
                case SUFFIX -> new DestinationResolver.BySuffix(kafka.topicSuffix());
                case FIXED -> new DestinationResolver.Fixed(kafka.fixedTopic());
                case BY_EVENT_TYPE -> new DestinationResolver.ByEventType(
                        kafka.topicByEventType(), kafka.fixedTopic());
            };
        }

        @Bean
        KafkaOutboxPublisher kafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                  DestinationResolver resolver,
                                                  OutboxProperties properties) {
            return new KafkaOutboxPublisher(kafkaTemplate, resolver, properties.retry().sendTimeout());
        }
    }

    // ---------- поллер ----------

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "outbox.polling", name = "enabled", matchIfMissing = true)
    static class PollingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        OutboxPoller outboxPoller(OutboxStore store,
                                  OutboxPublisher publisher,
                                  OutboxProperties properties,
                                  ObjectProvider<MeterRegistry> meters) {
            return new OutboxPoller(store, publisher, properties,
                    meters.getIfAvailable(SimpleMeterRegistry::new));
        }

        @Bean
        @ConditionalOnBean(JpaOutboxStore.class)
        @ConditionalOnMissingBean
        OutboxMaintenance outboxMaintenance(JpaOutboxStore store, OutboxProperties properties) {
            return new OutboxMaintenance(store, properties);
        }
    }

    // ---------- health ----------

    @Bean
    OutboxHealthIndicator outboxHealthIndicator(OutboxJpaRepository repository) {
        return new OutboxHealthIndicator(repository);
    }
}
