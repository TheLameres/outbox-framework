package io.txbox.consumer.starter.integration;

import io.txbox.consumer.jpa.repository.InboxJpaRepository;
import io.txbox.consumer.registrar.FlakyPingListener;
import io.txbox.core.model.MessageStatus;
import io.txbox.core.model.OutboxMessage;
import io.txbox.kafka.converter.OutboxMessageKafkaConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Сквозной интеграционный тест приёмника на реальных Kafka и PostgreSQL.
 *
 * <h2>Что проверяется</h2>
 *
 * <p>Сценарий «временная ошибка, затем успех»: handler падает на первой попытке
 * с исключением, которое классификатор превращает в
 * {@link io.txbox.consumer.outcome.ProcessingOutcome.Retryable}, а на второй
 * отрабатывает успешно.
 *
 * <p>Ожидание: сообщение доставлено повторно, обработано, в inbox ровно одна
 * запись со статусом {@code PROCESSED}.
 *
 * <h2>Какой баг ловится</h2>
 *
 * <p>{@code InboxKafkaListener.applyOutcome()} при исходе {@code Retryable}
 * только пишет в лог и возвращается — без {@code ack} и без исключения.
 * Spring Kafka перематывает партицию назад только в ответ на исключение
 * (это делает {@code DefaultErrorHandler}), поэтому повторной доставки
 * не происходит: handler вызывается один раз, запись остаётся в
 * {@code RECEIVED}, сообщение фактически теряется.
 *
 * <p>На коде с багом {@code retryableOutcome_isRedeliveredAndProcessed}
 * падает по таймауту — счётчик попыток остаётся равным 1.
 *
 * <h2>Запуск</h2>
 *
 * <p>Нужен Docker: поднимаются контейнеры {@code postgres:latest} и
 * {@code apache/kafka:latest}. Первый запуск занимает время на скачивание
 * образов, сам тест идёт несколько секунд.
 *
 * <pre>{@code
 * mvn -pl txbox-consumer/txbox-consumer-starter -am test
 * }</pre>
 */
@SpringBootTest(
        classes = IntegrationRetryableTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // Та же схема inbox, что и в проде: проверяем миграцию, а не
                // сгенерированную Hibernate DDL.
                "spring.liquibase.change-log=classpath:/db/changelog/consumer/v1-cumulative-changelog.xml",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",

                "txbox.consumer.kafka.topic=" + IntegrationRetryableTest.TOPIC,
                // Отдельная группа на каждый прогон, чтобы повторные запуски
                // не читали смещения предыдущих и не мешали другим тестам.
                "txbox.consumer.kafka.group-id=integration-retryable-${random.uuid}",
                // Без этого новая группа стартует с latest и пропускает сообщение,
                // отправленное до назначения партиции (топик создаётся первой отправкой).
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "txbox.consumer.kafka.poll-timeout-ms=1000",
                "txbox.consumer.kafka.concurrency=1",

                // Пакет, который сканирует DomainEventScanner
                "txbox.consumer.domain-event-base-packages=io.txbox.consumer.starter.integration"
        }
)
@Import(TestContainersConfiguration.class)
class IntegrationRetryableTest {

    static final String TOPIC = "txbox-integration-retryable";

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    InboxJpaRepository inboxRepository;

    @Autowired
    FlakyPingListener listener;

    @BeforeEach
    void reset() {
        listener.reset();
        inboxRepository.deleteAll();
    }

    // ── тесты ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Retryable на первой попытке → повторная доставка → PROCESSED")
    void retryableOutcome_isRedeliveredAndProcessed() throws Exception {
        var messageId = UUID.randomUUID();
        sendPing(messageId, "hello");

        // Ключевая проверка. На коде с багом (нет исключения при Retryable)
        // handler вызывается ровно один раз, и тест падает здесь по таймауту.
        await()
                .atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> assertThat(listener.attempts(messageId))
                        .as("handler должен быть вызван повторно после Retryable")
                        .isGreaterThanOrEqualTo(2));

        await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    var stored = inboxRepository.findById(messageId);
                    assertThat(stored)
                            .as("сообщение должно остаться в inbox")
                            .isPresent();
                    assertThat(stored.get().getStatus())
                            .as("после успешной повторной обработки — PROCESSED")
                            .isEqualTo(MessageStatus.PROCESSED);
                });
    }

    @Test
    @DisplayName("Успех с первой попытки: один вызов, PROCESSED")
    void successfulFirstAttempt_isProcessedOnce() throws Exception {
        var messageId = UUID.randomUUID();
        sendPing(messageId, TestPing.SUCCEED_IMMEDIATELY);

        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    var stored = inboxRepository.findById(messageId);
                    assertThat(stored).isPresent();
                    assertThat(stored.get().getStatus()).isEqualTo(MessageStatus.PROCESSED);
                });

        assertThat(listener.attempts(messageId))
                .as("без ошибки handler вызывается один раз")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Постоянная ошибка: FAILED, ровно один вызов, повторов нет")
    void fatalOutcome_marksFailedWithoutRetry() throws Exception {
        var messageId = UUID.randomUUID();
        sendPing(messageId, TestPing.FAIL_FATALLY);

        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    var stored = inboxRepository.findById(messageId);
                    assertThat(stored).isPresent();
                    assertThat(stored.get().getStatus())
                            .as("постоянная ошибка сохраняется как FAILED")
                            .isEqualTo(MessageStatus.FAILED);
                });

        // Даём время на возможный ошибочный повтор
        Thread.sleep(2_000);

        assertThat(listener.attempts(messageId))
                .as("Fatal не должен ретраиться")
                .isEqualTo(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Публикует сообщение теми же средствами, что и продюсер:
     * через {@link OutboxMessageKafkaConverter}, чтобы набор заголовков
     * {@code txbox-*} совпадал с боевым.
     */
    private void sendPing(UUID messageId, String text) throws Exception {
        var payload = "{\"text\":\"" + text + "\"}";

        var outboxMessage = new OutboxMessage(
                messageId,
                TestPing.AGGREGATE_TYPE,
                UUID.randomUUID().toString(),
                TestPing.EVENT_TYPE,
                payload,
                null,
                Instant.now(),
                0);

        var record = OutboxMessageKafkaConverter.toProducerRecord(outboxMessage, TOPIC);
        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
    }

    /**
     * Минимальное приложение для теста.
     *
     * <p>Не {@code @SpringBootApplication}: сканирование ограничено явно,
     * иначе в контекст попал бы {@link TestContainersConfiguration}
     * и другие тестовые бины.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "io.txbox.consumer.registrar")
    static class TestApplication {
    }
}
