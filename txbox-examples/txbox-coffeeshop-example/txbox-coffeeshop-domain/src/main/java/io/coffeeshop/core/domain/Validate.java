package io.coffeeshop.core.domain;

/**
 * Пакетно-приватные проверки инвариантов.
 *
 * <p>Вынесены отдельно, чтобы компактные конструкторы records не дублировали
 * одинаковые проверки. Класс не является частью публичного API домена.
 */
final class Validate {

    private Validate() {
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    static java.math.BigDecimal requirePositive(java.math.BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be > 0, got " + value);
        }
        return value;
    }

    static java.math.BigDecimal requireNonNegative(java.math.BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be >= 0, got " + value);
        }
        return value;
    }
}
