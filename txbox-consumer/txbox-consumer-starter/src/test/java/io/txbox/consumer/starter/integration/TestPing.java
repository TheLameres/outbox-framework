package io.txbox.consumer.starter.integration;

import io.txbox.core.event.DomainEvent;

/**
 * Тестовое событие.
 *
 * <p>Отдельный класс, а не вложенный record: {@code DomainEventScanner} ищет типы
 * по пакетам, и так он гарантированно попадает в реестр независимо от того,
 * как устроен тестовый класс.
 *
 * <p>{@code eventType} задан явно — по нему handler сопоставляется с сообщением.
 */
@DomainEvent(eventType = TestPing.EVENT_TYPE)
public record TestPing(String text) {

    public static final String EVENT_TYPE = "test.ping";
    public static final String AGGREGATE_TYPE = "Test";

    /** Маркер: handler не падает на первой попытке. */
    public static final String SUCCEED_IMMEDIATELY = "__succeed__";

    /**
     * Маркер: handler бросает постоянную ошибку.
     *
     * <p>{@code IllegalArgumentException} классификатор относит
     * к {@code ProcessingOutcome.Fatal}, повторов быть не должно.
     */
    public static final String FAIL_FATALLY = "__fatal__";
}
