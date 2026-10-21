package io.coffeeshop.order.entity;

import io.coffeeshop.core.domain.Money;
import io.coffeeshop.core.domain.OrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Строка заказа.
 *
 * <p>{@code line_no} фиксирует порядок строк: без него выборка из БД вернёт строки
 * в произвольном порядке, и состав заказа будет «прыгать» между перезапусками.
 */
@Entity
@Table(name = "order_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLineEntity {

    @Id
    private UUID id;

    @Column(name = "menu_item_id", nullable = false, updatable = false)
    private UUID menuItemId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceAmount;

    @Column(name = "unit_price_currency", nullable = false, length = 3)
    private String unitPriceCurrency;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    public static OrderLineEntity from(OrderLine line, int lineNo) {
        var entity = new OrderLineEntity();
        entity.id = UUID.randomUUID();
        entity.menuItemId = line.menuItemId();
        entity.name = line.name();
        entity.quantity = line.quantity();
        entity.unitPriceAmount = line.unitPrice().amount();
        entity.unitPriceCurrency = line.unitPrice().currency();
        entity.lineNo = lineNo;
        return entity;
    }

    public OrderLine toDomain() {
        return new OrderLine(
                menuItemId,
                name,
                quantity,
                new Money(new BigDecimal(unitPriceAmount.toPlainString()), unitPriceCurrency));
    }
}
