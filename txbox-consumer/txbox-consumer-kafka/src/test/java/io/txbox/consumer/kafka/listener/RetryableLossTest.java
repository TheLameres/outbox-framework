package io.txbox.consumer.kafka.listener;

import io.txbox.consumer.kafka.dispatcher.InboxEventDispatcher;
import io.txbox.consumer.outcome.ProcessingOutcome;
import io.txbox.consumer.store.InboxStore;
import io.txbox.kafka.header.TxBoxHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тест, демонстрирующий баг: при исходе Retryable listener пишет в лог и выходит без ack,
 * но и без исключения. Это ошибка, потому что:
 *
 * 1. При AckMode.MANUAL пропущенный ack не означает повторную доставку. Смещения Kafka
 *    накопительные: подтверждение сообщения N+1 зафиксирует смещение N+2, и сообщение N
 *    окажется потерянным.
 *
 * 2. Чтобы Spring Kafka начал повторную доставку, listener обязан бросить исключение.
 *    DefaultErrorHandler перемотает партицию назад, и сообщение вернётся.
 *
 * Проверяем:
 * - при Retryable listener бросает исключение (чтобы сработал DefaultErrorHandler);
 * - store.markFailed не вызывается (статус должен остаться RECEIVED, иначе повтор потеряет данные);
 * - ack не вызывается.
 */
class RetryableLossTest {

    InboxStore store = mock(InboxStore.class);
    InboxEventDispatcher dispatcher = mock(InboxEventDispatcher.class);
    Acknowledgment ack = mock(Acknowledgment.class);
    InboxKafkaListener listener = new InboxKafkaListener(store, dispatcher);

    final UUID messageId = UUID.randomUUID();

    @BeforeEach
    void init() {
        when(store.findByKafkaPosition(anyInt(), anyLong())).thenReturn(Optional.empty());
        when(store.wasProcessed(any())).thenReturn(false);
    }

    private ConsumerRecord<String, String> record(int partition, long offset) {
        var r = new ConsumerRecord<>("test.events", partition, offset, "key-1", "{}");
        r.headers().add(TxBoxHeaders.MESSAGE_ID, messageId.toString().getBytes(StandardCharsets.UTF_8));
        r.headers().add(TxBoxHeaders.EVENT_TYPE, "TestEvent".getBytes(StandardCharsets.UTF_8));
        r.headers().add(TxBoxHeaders.AGGREGATE_TYPE, "Test".getBytes(StandardCharsets.UTF_8));
        return r;
    }

    @Test
    void retryable_mustThrowException_otherwiseMessageIsLost() {
        // Dispatcher вернул Retryable: база не доступна, очередь заблокирована и т.п.
        when(dispatcher.dispatch(any(), any(), anyBoolean()))
                .thenReturn(new ProcessingOutcome.Retryable(messageId, "database down", null));

        // ТЕКУЩЕЕ ПОВЕДЕНИЕ (баг):
        // listener логирует "Event retryable: ..." и выходит без ack и без исключения.
        // Если следующее сообщение будет Success, оно вызовет ack, зафиксирует offset N+1,
        // и Kafka считает, что N тоже обработан. Но он остался в RECEIVED — потерян.

        // ОЖИДАЕМОЕ ПОВЕДЕНИЕ: listener бросает исключение, чтобы Spring Kafka
        // запустил DefaultErrorHandler и перемотал позицию назад.
        assertThatThrownBy(() -> listener.listen(record(0, 100), ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("database down");

        // Транзакция откатилась → ничего не сохранилось в inbox
        verify(store, never()).markProcessed(any());
        verify(store, never()).markFailed(any(), any());
        verify(store, never()).markSkipped(any(), any());

        // ack не вызван → смещение не зафиксировано
        verify(ack, never()).acknowledge();
    }

    @Test
    void retryable_afterMultipleAttempts_eventuallyMarkedFailed() {
        // Первый вызов Retryable, второй Fatal (DefaultErrorHandler исчерпал попытки)
        when(dispatcher.dispatch(any(), any(), anyBoolean()))
                .thenReturn(new ProcessingOutcome.Retryable(messageId, "database down", null))
                .thenReturn(new ProcessingOutcome.Fatal(messageId, "max retries", null));

        // Первая попытка: исключение
        assertThatThrownBy(() -> listener.listen(record(0, 100), ack))
                .isInstanceOf(RuntimeException.class);

        // Вторая попытка: Fatal → markFailed + ack (сообщение уйдёт в DLQ через error handler)
        listener.listen(record(0, 100), ack);

        verify(store).markFailed(messageId, "max retries");
        verify(ack).acknowledge();
    }

    @Test
    void success_acksImmediately() {
        when(dispatcher.dispatch(any(), any(), anyBoolean()))
                .thenReturn(new ProcessingOutcome.Success(messageId));

        listener.listen(record(0, 100), ack);

        verify(store).markProcessed(messageId);
        verify(ack).acknowledge();
    }
}
