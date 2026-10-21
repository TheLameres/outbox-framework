package io.coffeeshop.barista.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Заказ на приготовление (тикет на стойке).
 *
 * <p>Уникальный {@code order_id}: повторное {@code inventory.reserved} не должно
 * создавать второй тикет на тот же заказ.
 */
@Entity
@Table(
        name = "tickets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ticket_order", columnNames = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketEntity {

    /** Статус приготовления. */
    public enum Status {
        /** Тикет создан, ожидает бариста. */
        QUEUED,
        /** Напиток готовится. */
        PREPARING,
        /** Напиток готов. */
        PREPARED,
        /** Приготовить не удалось. */
        FAILED
    }

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "ticket_no", nullable = false, length = 32)
    private String ticketNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private Status status;

    @Column(name = "failure_reason", length = 500)
    @Setter
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    @Setter
    private Instant startedAt;

    @Column(name = "prepared_at")
    @Setter
    private Instant preparedAt;

    public static TicketEntity queued(UUID orderId, String ticketNo) {
        var entity = new TicketEntity();
        entity.id = UUID.randomUUID();
        entity.orderId = orderId;
        entity.ticketNo = ticketNo;
        entity.status = Status.QUEUED;
        entity.createdAt = Instant.now();
        return entity;
    }
}
