package io.txbox.producer.starter.polling;

import io.txbox.producer.starter.config.OutboxProperties;
import io.txbox.producer.starter.maintenance.OutboxMaintenance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;

import java.util.concurrent.ScheduledFuture;

/**
 * Управляет динамическим расписанием поллера и обслуживания outbox.
 *
 * <p>Регистрирует задачи через {@link TaskScheduler} вместо {@code @Scheduled},
 * чтобы интервалы можно было брать из конфигурации без перезапуска.
 *
 * <p>Останавливает задачи при завершении контекста через {@link DisposableBean}.
 */
@Slf4j
public class OutboxSchedulerManager implements InitializingBean, DisposableBean {

    private final TaskScheduler scheduler;
    private final OutboxProperties properties;
    private final OutboxPoller poller;
    private final OutboxMaintenance maintenance;

    private ScheduledFuture<?> pollFuture;
    private ScheduledFuture<?> maintenanceFuture;

    public OutboxSchedulerManager(TaskScheduler scheduler,
                                  OutboxProperties properties,
                                  OutboxPoller poller,
                                  OutboxMaintenance maintenance) {
        this.scheduler = scheduler;
        this.properties = properties;
        this.poller = poller;
        this.maintenance = maintenance;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.enabled()) {
            log.info("OutboxSchedulerManager: outbox disabled, skipping scheduler registration");
            return;
        }

        if (properties.polling().enabled()) {
            pollFuture = scheduler.scheduleWithFixedDelay(
                    poller::poll,
                    properties.polling().interval());
            log.info("OutboxSchedulerManager: poll scheduled every {}", properties.polling().interval());
        }

        if (properties.maintenance().enabled()) {
            maintenanceFuture = scheduler.scheduleWithFixedDelay(
                    maintenance::run,
                    properties.maintenance().interval());
            log.info("OutboxSchedulerManager: maintenance scheduled every {}", properties.maintenance().interval());
        }
    }

    @Override
    public void destroy() {
        cancel(pollFuture, "poll");
        cancel(maintenanceFuture, "maintenance");
    }

    private void cancel(ScheduledFuture<?> future, String name) {
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.info("OutboxSchedulerManager: {} task cancelled", name);
        }
    }
}
