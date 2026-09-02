package com.company.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.outbox.core.OutboxMessage;
import com.company.outbox.core.OutboxStore;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Проверяет главное свойство SKIP LOCKED: два конкурентных claim
 * НЕ возвращают одно и то же сообщение.
 */
@SpringBootTest
class OutboxConcurrencyTest {

    @Autowired
    OutboxStore store;

    @Test
    void concurrentClaims_shouldNotOverlap() throws Exception {
        // Виртуальные потоки в тестах — дешёвая конкурентность без пулов
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<List<OutboxMessage>>> tasks =
                    List.of(() -> store.claimBatch(50), () -> store.claimBatch(50));

            var futures = executor.invokeAll(tasks);
            var first = futures.get(0).get();
            var second = futures.get(1).get();

            var firstIds = first.stream().map(OutboxMessage::id).collect(java.util.stream.Collectors.toSet());
            var secondIds = second.stream().map(OutboxMessage::id).collect(java.util.stream.Collectors.toSet());

            assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
        }
    }
}
