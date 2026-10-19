package io.coffeeshop.core.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Строка заказа.
 *
 * <p>Название и цена копируются из позиции меню на момент оформления: если завтра
 * цена изменится, уже оформленный заказ не должен пересчитываться.
 *
 * @param menuItemId позиция меню
 * @param name       название на момент заказа
 * @param quantity   количество порций
 * @param unitPrice  цена одной порции на момент заказа
 */
public record OrderLine(UUID menuItemId, String name, int quantity, Money unitPrice) {

    public OrderLine {
        Objects.requireNonNull(menuItemId, "menuItemId");
        name = Validate.requireText(name, "name");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0, got " + quantity);
        }
        Objects.requireNonNull(unitPrice, "unitPrice");
    }

    public Money total() {
        return unitPrice.times(quantity);
    }
}
