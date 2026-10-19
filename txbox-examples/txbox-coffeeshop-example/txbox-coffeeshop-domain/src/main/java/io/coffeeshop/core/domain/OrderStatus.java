package io.coffeeshop.core.domain;

import java.util.Set;

/**
 * Жизненный цикл заказа.
 *
 * <p>Переходы описаны данными, а не разбросаны по сервисам: правила саги
 * (подтверждение, отказ, компенсация) должны проверяться в одном месте.
 *
 * <pre>
 * PLACED ──▶ CONFIRMED ──▶ PREPARING ──▶ READY ──▶ COMPLETED
 *   │            │              │
 *   ├──▶ REJECTED└──▶ CANCELLED ┘
 *   └──▶ CANCELLED
 * </pre>
 *
 * <p>{@code REJECTED} — отказ по бизнес-причине (нет ингредиентов).
 * {@code CANCELLED} — отмена инициатором. Оба статуса терминальны.
 */
public enum OrderStatus {

    /** Заказ принят, событие {@code order.placed} опубликовано. */
    PLACED,

    /** Склад зарезервировал ингредиенты. */
    CONFIRMED,

    /** Бариста начал готовить. */
    PREPARING,

    /** Напиток готов, ожидает выдачи. */
    READY,

    /** Заказ выдан гостю. */
    COMPLETED,

    /** Отменён инициатором; резерв должен быть освобождён. */
    CANCELLED,

    /** Отклонён: ингредиентов не хватило. */
    REJECTED;

    private static final Set<OrderStatus> TERMINAL = Set.of(COMPLETED, CANCELLED, REJECTED);

    /**
     * Допустимые переходы из текущего статуса.
     */
    public Set<OrderStatus> allowedTransitions() {
        return switch (this) {
            case PLACED -> Set.of(CONFIRMED, REJECTED, CANCELLED);
            case CONFIRMED -> Set.of(PREPARING, CANCELLED);
            case PREPARING -> Set.of(READY, CANCELLED);
            case READY -> Set.of(COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED, REJECTED -> Set.of();
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedTransitions().contains(target);
    }

    /**
     * Терминальный статус: дальнейшие переходы невозможны, компенсации не требуются.
     */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
