package io.coffeeshop.core.domain;

import java.math.BigDecimal;

/**
 * Не хватает ингредиента на складе.
 *
 * <p>Как и {@link OrderStateException}, наследуется от {@link IllegalStateException}:
 * это решение бизнеса, а не сбой инфраструктуры, повторять операцию бессмысленно.
 */
public class InsufficientStockException extends IllegalStateException {

    private final String ingredientCode;
    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientStockException(String ingredientCode,
                                      BigDecimal requested,
                                      BigDecimal available) {
        super("Insufficient stock for " + ingredientCode
                + ": requested " + requested.toPlainString()
                + ", available " + available.toPlainString());
        this.ingredientCode = ingredientCode;
        this.requested = requested;
        this.available = available;
    }

    public String ingredientCode() {
        return ingredientCode;
    }

    public BigDecimal requested() {
        return requested;
    }

    public BigDecimal available() {
        return available;
    }
}
