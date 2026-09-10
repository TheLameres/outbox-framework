package io.txbox.consumer.starter.config;

import io.txbox.consumer.jpa.entity.InboxMessageEntity;
import io.txbox.consumer.kafka.configuration.InboxConfiguration;
import io.txbox.consumer.kafka.listener.InboxKafkaListener;
import io.txbox.consumer.kafka.dispatcher.InboxEventDispatcher;
import io.txbox.consumer.jpa.repository.InboxJpaRepository;
import io.txbox.consumer.jpa.store.JpaInboxStore;
import io.txbox.consumer.starter.health.InboxHealthIndicator;
import io.txbox.consumer.starter.registrar.InboxKafkaListenerRegistrar;
import io.txbox.consumer.store.InboxStore;
import io.txbox.jpa.TxBoxJpaPackagesCustomizer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.adapter.KafkaMessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Spring Boot auto-configuration для consumer-side (inbox).
 * Регистрирует бины для Kafka listener'а и JPA store.
 */
@AutoConfiguration
@EnableConfigurationProperties(InboxProperties.class)
@EnableJpaRepositories(basePackages = "io.txbox.consumer.jpa.repository",
        entityManagerFactoryRef = "txBoxEntityManagerFactoryBean")
@RequiredArgsConstructor
public class InboxAutoConfiguration {

    private final InboxProperties properties;

    @Bean
    public TxBoxJpaPackagesCustomizer inBoxJpaPackagesCustomizer() {
        return () -> List.of("io.txbox.consumer.jpa.entity");
    }

    @Bean
    public InboxStore inboxStore(InboxJpaRepository repository) {
        return new JpaInboxStore(repository);
    }

    @Bean
    public InboxEventDispatcher inboxEventDispatcher(ApplicationContext context,
                                                     ObjectMapper objectMapper) {
        return new InboxEventDispatcher(context, objectMapper, properties);
    }

    @Bean
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

    @Bean
    public DefaultMessageHandlerMethodFactory kafkaMessageHandlerMethodFactory() {
        return new DefaultMessageHandlerMethodFactory();
    }

    @Bean
    public InboxKafkaListenerRegistrar inboxKafkaListenerRegistrar(
            KafkaListenerEndpointRegistry registry,
            KafkaListenerContainerFactory<?> inboxListenerContainerFactory,
            InboxConfiguration inboxConfiguration,
            InboxKafkaListener handler,
            DefaultMessageHandlerMethodFactory messageHandlerMethodFactory
    ) {
        return new InboxKafkaListenerRegistrar(
                registry,
                inboxListenerContainerFactory,
                inboxConfiguration,
                handler,
                messageHandlerMethodFactory
        );
    }

    @Bean
    public InboxHealthIndicator inboxHealthIndicator(InboxStore inboxStore) {
        return new InboxHealthIndicator(inboxStore);
    }
}
