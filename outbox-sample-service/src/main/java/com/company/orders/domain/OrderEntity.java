package com.company.orders.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
