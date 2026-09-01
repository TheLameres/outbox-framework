package com.company.outbox.core;

public enum OutboxStatus {
    PENDING,
    IN_FLIGHT,
    PROCESSED,
    FAILED;

    public boolean isTerminal() {
        // Улучшенный switch-expression: exhaustive, без default и без break
        return switch (this) {
            case PROCESSED, FAILED -> true;
            case PENDING, IN_FLIGHT -> false;
        };
    }
}
