package com.somecompany.project.fixture;

import io.txbox.core.event.DomainEvent;

@DomainEvent(eventType = "event.name")
public record EventWithName() {
}
