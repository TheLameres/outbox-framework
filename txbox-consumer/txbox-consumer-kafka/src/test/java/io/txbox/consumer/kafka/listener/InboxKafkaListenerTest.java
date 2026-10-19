package io.txbox.consumer.kafka.listener;

import io.txbox.consumer.kafka.dispatcher.InboxEventDispatcher;
import io.txbox.consumer.outcome.ProcessingOutcome;
import io.txbox.consumer.store.InboxStore;
import io.txbox.core.model.InboxMessage;
import io.txbox.kafka.header.TxBoxHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class InboxKafkaListenerTest {

    InboxStore store = mock(InboxStore.class);
    InboxEventDispatcher dispatcher = mock(InboxEventDispatcher.class);
    Acknowledgment ack = mock(Acknowledgment.class);
    InboxKafkaListener listener = new InboxKafkaListener(store, dispatcher);

    final UUID id = UUID.randomUUID();

    @BeforeEach
    void init() {
        when(store.findByKafkaPosition(anyInt(), anyLong())).thenReturn(Optional.empty());
    }

    private ConsumerRecord<String, String> record(int partition, long offset) {
        var r = new ConsumerRecord<>("coffee.events", partition, offset, "key-1", "{}");
        r.headers().add(TxBoxHeaders.MESSAGE_ID, id.toString().getBytes(StandardCharsets.UTF_8));
        r.headers().add(TxBoxHeaders.EVENT_TYPE, "Ping".getBytes(StandardCharsets.UTF_8));
        r.headers().add(TxBoxHeaders.AGGREGATE_TYPE, "Order".getBytes(StandardCharsets.UTF_8));
        return r;
    }

    private InboxMessage stored(int partition, long offset) {
        return new InboxMessage(id, "Order", "key-1", "Ping", "{}", new LinkedHashMap<>(),
                Instant.now(), Instant.now(), partition, offset);
    }

    @Test
    void sameMessageId_atNewOffset_isTreatedAsDuplicate_andNotOverwritten() {
        when(store.wasProcessed(id)).thenReturn(true);
        when(dispatcher.dispatch(any(), any(), eq(true)))
                .thenReturn(new ProcessingOutcome.Skipped(id, "duplicate messageId"));

        listener.listen(record(0, 42), ack);

        verify(dispatcher).dispatch(any(), any(), eq(true));
        verify(store, never()).markSkipped(any(), any());   // терминальный статус не затираем
        verify(store, never()).markProcessed(any());
        verify(store, never()).markFailed(any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void firstDelivery_success_marksProcessed_andAcks() {
        when(store.wasProcessed(id)).thenReturn(false);
        when(dispatcher.dispatch(any(), any(), eq(false)))
                .thenReturn(new ProcessingOutcome.Success(id));

        listener.listen(record(0, 1), ack);

        verify(store).markProcessed(id);
        verify(ack).acknowledge();
    }

    @Test
    void retryable_isNotAcked() {
        when(store.wasProcessed(id)).thenReturn(false);
        when(dispatcher.dispatch(any(), any(), anyBoolean()))
                .thenReturn(new ProcessingOutcome.Retryable(id, "db down", new RuntimeException()));

        listener.listen(record(0, 1), ack);

        verify(ack, never()).acknowledge();
    }

    @Test
    void redeliveryOfSamePosition_afterRetryable_isReprocessed_notDropped() {
        // запись осталась в RECEIVED после Retryable -> wasProcessed == false
        when(store.findByKafkaPosition(0, 1)).thenReturn(Optional.of(stored(0, 1)));
        when(store.wasProcessed(id)).thenReturn(false);
        when(dispatcher.dispatch(any(), any(), eq(false)))
                .thenReturn(new ProcessingOutcome.Success(id));

        listener.listen(record(0, 1), ack);

        verify(dispatcher).dispatch(any(), any(), eq(false));
        verify(store).markProcessed(id);
        verify(ack).acknowledge();
    }

    @Test
    void redeliveryOfSamePosition_afterCompletion_isAckedWithoutDispatch() {
        when(store.findByKafkaPosition(0, 1)).thenReturn(Optional.of(stored(0, 1)));
        when(store.wasProcessed(id)).thenReturn(true);

        listener.listen(record(0, 1), ack);

        verify(dispatcher, never()).dispatch(any(), any(), anyBoolean());
        verify(ack).acknowledge();
    }
}
