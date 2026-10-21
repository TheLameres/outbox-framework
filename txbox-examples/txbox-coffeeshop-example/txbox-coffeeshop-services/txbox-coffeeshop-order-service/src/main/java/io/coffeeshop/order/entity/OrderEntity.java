package io.coffeeshop.order.entity;

import io.coffeeshop.core.domain.Order;
import io.coffeeshop.core.domain.OrderLine;
import io.coffeeshop.core.domain.OrderStatus;
import io.coffeeshop.core.domain.Money;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Заказ в базе сервиса заказов.
 *
 * <p>Класс изменяемый и с конструктором без аргументов — этого требует JPA.
 * Доменный {@link Order} остаётся неизменяемым record'ом, а правила перехода
 * статусов живут в нём; сущность — только способ хранения.
 *
 * <p>Деньги разложены на две колонки: JPA не умеет маппить {@link Money} напрямую,
 * а {@code @Embeddable} добавил бы класс ради двух полей.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_currency", nullable = false, length = 3)
    private String totalCurrency;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Строки заказа. {@code cascade = ALL} + {@code orphanRemoval}: состав заказа
     * меняется только вместе с заказом, отдельного жизненного цикла у строк нет.
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderLineEntity> lines = new ArrayList<>();

    // ── mapping ───────────────────────────────────────────────────────────────

    public static OrderEntity from(Order order) {
        var entity = new OrderEntity();
        entity.id = order.id();
        entity.status = order.status();
        entity.placedAt = order.placedAt();
        entity.failureReason = order.failureReason();

        Money total = order.total();
        entity.totalAmount = total.amount();
        entity.totalCurrency = total.currency();

        int lineNo = 0;
        for (OrderLine line : order.lines()) {
            entity.lines.add(OrderLineEntity.from(line, lineNo++));
        }

        Instant now = Instant.now();
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    /**
     * Проекция в доменный агрегат. Дальнейшие переходы статуса выполняются
     * на нём, а не на сущности.
     */
    public Order toDomain() {
        return new Order(
                id,
                lines.stream().map(OrderLineEntity::toDomain).toList(),
                status,
                placedAt,
                failureReason);
    }

    /**
     * Переносит состояние доменного агрегата в сущность.
     *
     * <p>Вызывается после каждого перехода статуса: домен решает, что можно,
     * а сущность просто сохраняет результат.
     */
    public void apply(Order order) {
        this.status = order.status();
        this.failureReason = order.failureReason();

        Money total = order.total();
        this.totalAmount = total.amount();
        this.totalCurrency = total.currency();
        this.updatedAt = Instant.now();
    }
}
