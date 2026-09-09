package io.txbox.consumer.starter.registrar;

import io.txbox.consumer.kafka.configuration.InboxConfiguration;
import io.txbox.consumer.kafka.listener.InboxKafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.kafka.config.MethodKafkaListenerEndpoint;

import java.lang.reflect.Method;

public class InboxKafkaListenerRegistrar implements ApplicationListener<ApplicationReadyEvent> {

    private final KafkaListenerEndpointRegistry registry;
    private final KafkaListenerContainerFactory<?> inboxListenerContainerFactory;
    private final InboxConfiguration inboxConfiguration;
    private final InboxKafkaListener handler;
    private final DefaultMessageHandlerMethodFactory messageHandlerMethodFactory;

    public InboxKafkaListenerRegistrar(
            KafkaListenerEndpointRegistry registry,
            KafkaListenerContainerFactory<?> inboxListenerContainerFactory,
            InboxConfiguration inboxConfiguration,
            InboxKafkaListener handler,
            DefaultMessageHandlerMethodFactory messageHandlerMethodFactory) {
        this.registry = registry;
        this.inboxListenerContainerFactory = inboxListenerContainerFactory;
        this.inboxConfiguration = inboxConfiguration;
        this.handler = handler;
        this.messageHandlerMethodFactory = messageHandlerMethodFactory;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        MethodKafkaListenerEndpoint<String, String> endpoint =
                new MethodKafkaListenerEndpoint<>();

        endpoint.setId("inbox-listener");
        endpoint.setTopics(inboxConfiguration.kafka().topic());   // вместо SpEL
        endpoint.setGroupId(inboxConfiguration.kafka().groupId());

        // Указываем Spring-прокси бин — иначе @Transactional не применится!
        endpoint.setBean(handler);
        endpoint.setMethod(resolveMethod());

        endpoint.setMessageHandlerMethodFactory(messageHandlerMethodFactory);

        // AckMode должен быть MANUAL в inboxListenerContainerFactory
        registry.registerListenerContainer(endpoint, inboxListenerContainerFactory, true);
    }

    private Method resolveMethod() {
        try {
            return InboxKafkaListener.class.getMethod(
                    "listen", ConsumerRecord.class, Acknowledgment.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Cannot find InboxKafkaHandler#listen", e);
        }
    }
}