package com.company.orders.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrderEntity {

    public enum Status { NEW, COMPLETED, CANCELLED }

    @Id
    @ToString.Include
    @EqualsAndHashCode.Include
    private UUID id;

    private UUID customerId;
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @ToString.Include
    private Status status;

    private Instant createdAt;

    static OrderEntity create(UUID customerId, BigDecimal total) {
        var order = new OrderEntity();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.total = total;
        order.status = Status.NEW;
        order.createdAt = Instant.now();
        return order;
    }

    void markCompleted() {
        this.status = Status.COMPLETED;
    }

    void markCancelled() {
        this.status = Status.CANCELLED;
    }

    boolean isPaid() {
        return status == Status.COMPLETED;
    }
}
