package io.txbox.producer.api;

import io.txbox.core.model.OutboxMessage;

/**
 * Контракт брокера-публикатора. Реализации: Kafka, RabbitMQ, SNS, HTTP-webhook.
 *
 * <p>Метод {@link #publish} никогда не бросает исключений — все исходы
 * инкапсулированы в {@link PublishOutcome}. Это позволяет поллеру
 * обрабатывать результаты единообразно через exhaustive switch.
 */
public interface OutboxPublisher {

    /**
     * Публикует сообщение в брокер.
     * Вызывается вне транзакции (между TX#2 и TX#3).
     *
     * @return исход публикации — Published, Retryable, Fatal или Skipped
     */
    PublishOutcome publish(OutboxMessage message);
}
