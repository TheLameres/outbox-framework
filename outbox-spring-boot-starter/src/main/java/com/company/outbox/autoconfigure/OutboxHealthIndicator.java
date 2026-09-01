package com.company.outbox.autoconfigure;

import com.company.outbox.core.OutboxStatus;
import com.company.outbox.jpa.OutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** /actuator/health/outbox — DOWN, если накопились FAILED-сообщения. */
@RequiredArgsConstructor
public class OutboxHealthIndicator implements HealthIndicator {

    private static final long FAILED_THRESHOLD = 1;

    private final OutboxJpaRepository repository;

    @Override
    public Health health() {
        long pending = repository.countByStatus(OutboxStatus.PENDING);
        long inFlight = repository.countByStatus(OutboxStatus.IN_FLIGHT);
        long failed = repository.countByStatus(OutboxStatus.FAILED);

        var builder = failed >= FAILED_THRESHOLD ? Health.down() : Health.up();
        return builder
                .withDetail("pending", pending)
                .withDetail("inFlight", inFlight)
                .withDetail("failed", failed)
                .build();
    }
}
