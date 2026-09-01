package com.company.outbox.core;

/** Контракт брокера. Реализации: Kafka, RabbitMQ, SNS, HTTP-webhook. */
@FunctionalInterface
public interface OutboxPublisher {

    /**
     * Публикует сообщение. Не должен бросать исключения — все ошибки
     * возвращаются как {@link PublishOutcome.Retryable} / {@link PublishOutcome.Fatal}.
     */
    PublishOutcome publish(OutboxMessage message);
}
