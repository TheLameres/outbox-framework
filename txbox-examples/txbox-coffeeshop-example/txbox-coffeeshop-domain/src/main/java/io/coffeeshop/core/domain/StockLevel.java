package io.coffeeshop.core.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Остаток ингредиента на складе.
 *
 * <p>Разделены два числа:
 * <ul>
 *   <li>{@code onHand} — физически на складе;</li>
 *   <li>{@code reserved} — зарезервировано под заказы, но ещё не списано.</li>
 * </ul>
 * Разница {@link #available()} — то, что можно обещать новым заказам.
 * Разделение обязательно: иначе отмена заказа не сможет вернуть ингредиент,
 * который уже был списан.
 *
 * <p>Все операции возвращают новый экземпляр, поэтому остаток нельзя
 * изменить в обход проверок.
 */
public record StockLevel(String ingredientCode,
                         Unit unit,
                         BigDecimal onHand,
                         BigDecimal reserved) {

    public StockLevel {
        ingredientCode = Validate.requireText(ingredientCode, "ingredientCode");
        Objects.requireNonNull(unit, "unit");
        onHand = Validate.requireNonNegative(onHand, "onHand");
        reserved = Validate.requireNonNegative(reserved, "reserved");
        if (reserved.compareTo(onHand) > 0) {
            throw new IllegalArgumentException(
                    "reserved (" + reserved.toPlainString()
                            + ") cannot exceed onHand (" + onHand.toPlainString()
                            + ") for " + ingredientCode);
        }
    }

    public static StockLevel empty(String ingredientCode, Unit unit) {
        return new StockLevel(ingredientCode, unit, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Доступно к резервированию.
     */
    public BigDecimal available() {
        return onHand.subtract(reserved);
    }

    /**
     * Резервирует количество под заказ. Остаток на складе не меняется —
     * меняется только {@code reserved}.
     *
     * @throws InsufficientStockException если доступного количества не хватает
     */
    public StockLevel reserve(BigDecimal amount) {
        Validate.requirePositive(amount, "amount");
        if (available().compareTo(amount) < 0) {
            throw new InsufficientStockException(ingredientCode, amount, available());
        }
        return new StockLevel(ingredientCode, unit, onHand, reserved.add(amount));
    }

    /**
     * Освобождает резерв без списания — компенсация при отмене или отказе.
     */
    public StockLevel release(BigDecimal amount) {
        Validate.requirePositive(amount, "amount");
        if (reserved.compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "cannot release " + amount.toPlainString()
                            + " of " + ingredientCode + ": only "
                            + reserved.toPlainString() + " reserved");
        }
        return new StockLevel(ingredientCode, unit, onHand, reserved.subtract(amount));
    }

    /**
     * Списывает зарезервированное количество — ингредиент израсходован.
     */
    public StockLevel consume(BigDecimal amount) {
        Validate.requirePositive(amount, "amount");
        if (reserved.compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "cannot consume " + amount.toPlainString()
                            + " of " + ingredientCode + ": only "
                            + reserved.toPlainString() + " reserved");
        }
        return new StockLevel(ingredientCode, unit,
                onHand.subtract(amount), reserved.subtract(amount));
    }

    /**
     * Пополнение склада.
     */
    public StockLevel replenish(BigDecimal amount) {
        Validate.requirePositive(amount, "amount");
        return new StockLevel(ingredientCode, unit, onHand.add(amount), reserved);
    }
}
