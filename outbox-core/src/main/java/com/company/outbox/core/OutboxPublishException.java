package com.company.outbox.core;

/** Бросается только из инфраструктурного кода, который не может вернуть PublishOutcome. */
public class OutboxPublishException extends RuntimeException {

    public OutboxPublishException(String message) {
        super(message);
    }

    public OutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
