package io.txbox.consumer.starter.config;

import io.txbox.consumer.kafka.listener.InboxKafkaListener;
import io.txbox.consumer.kafka.dispatcher.InboxEventDispatcher;
import io.txbox.consumer.jpa.repository.InboxJpaRepository;
import io.txbox.consumer.jpa.store.JpaInboxStore;
import io.txbox.consumer.store.InboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Spring Boot auto-configuration для consumer-side (inbox).
 * Регистрирует бины для Kafka listener'а и JPA store.
 */
@Configuration
@EnableKafka
@EnableTransactionManagement
@EnableConfigurationProperties(InboxProperties.class)
@RequiredArgsConstructor
public class InboxAutoConfiguration {

    private final InboxProperties properties;
    private final InboxJpaRepository repository;

    @Bean
    @ConditionalOnMissingBean
    public InboxStore inboxStore() {
        return new JpaInboxStore(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    public InboxEventDispatcher inboxEventDispatcher(ApplicationContext context) {
        return new InboxEventDispatcher(context);
    }

    @Bean
    @ConditionalOnMissingBean
    public InboxKafkaListener inboxKafkaListener(InboxStore store,
                                                 InboxEventDispatcher dispatcher) {
        return new InboxKafkaListener(store, dispatcher);
    }

    /**
     * Kafka listener container factory — настраивает transactional semantics
     * и manual offset management для inbox.
     */
    @Bean(name = "inboxListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String>
    inboxListenerContainerFactory(ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // Manual commit: после успешной обработки в listener'е
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Concurrency — параллельная обработка тредов (если нужно; по умолчанию 1)
        factory.setConcurrency(properties.kafka().concurrency());

        // Poll timeout
        factory.getContainerProperties()
                .setPollTimeout(properties.kafka().pollTimeoutMs());

        return factory;
    }
}
