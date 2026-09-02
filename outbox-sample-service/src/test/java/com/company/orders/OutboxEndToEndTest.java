package com.company.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.company.orders.domain.OrderEvent;
import com.company.orders.domain.OrderEventRouting;
import com.company.orders.domain.OrderService;
import com.company.outbox.core.OutboxStatus;
import com.company.outbox.jpa.OutboxJpaRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Тесты outbox гоняем ТОЛЬКО на реальном PostgreSQL: H2 молча игнорирует
 * SKIP LOCKED, и тест «зелёный» при сломанной конкурентности.
 */
@SpringBootTest
@Import(TestConfiguration.class)
class OutboxEndToEndTest {

    @Autowired
    OrderService orderService;

    @Autowired
    OutboxJpaRepository outboxRepository;

    @Test
    void createOrder_shouldStageAndPublishEvent() {
        UUID orderId = orderService.createOrder(UUID.randomUUID(), new BigDecimal("199.99"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var events = outboxRepository
                    .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("Order", orderId.toString());

            assertThat(events)
                    .singleElement()
                    .satisfies(e -> {
                        assertThat(e.getEventType()).isEqualTo("Created");
                        assertThat(e.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
                        assertThat(e.getHeaders()).containsKey("schema-version");
                    });
        });
    }

    @Test
    void routing_shouldBeExhaustiveForAllEventTypes() {
        // Компилятор гарантирует полноту switch; тест фиксирует конкретные топики
        var created = OrderEvent.Created.now(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE);
        var refund = new OrderEvent.Cancelled(
                UUID.randomUUID(), "fraud", true, java.time.Instant.now());

        assertThat(OrderEventRouting.topicFor(created)).isEqualTo("orders.created");
        assertThat(OrderEventRouting.topicFor(refund)).isEqualTo("orders.refunds");
    }

    @Test
    void createdEvent_shouldRejectNonPositiveTotal() {
        // Валидация из компактного конструктора record-а
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> OrderEvent.Created.now(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO));
    }
}
