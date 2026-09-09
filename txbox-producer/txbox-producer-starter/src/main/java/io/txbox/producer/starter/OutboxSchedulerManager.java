package io.txbox.producer.starter;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

public class OutboxSchedulerManager implements InitializingBean, DisposableBean {

    private final TaskScheduler taskScheduler;
    private final OutboxProperties outboxProperties;
    private final OutboxPoller outboxPoller;
    private final OutboxMaintenance outboxMaintenance;

    private final List<ScheduledFuture<?>> scheduledTask;


    public OutboxSchedulerManager(TaskScheduler taskScheduler,
                                  OutboxProperties outboxProperties,
                                  OutboxPoller outboxPoller,
                                  OutboxMaintenance outboxMaintenance) {
        this.taskScheduler = taskScheduler;
        this.outboxProperties = outboxProperties;
        this.outboxPoller = outboxPoller;
        this.outboxMaintenance = outboxMaintenance;
        this.scheduledTask = new ArrayList<>();
    }

    @Override
    public void destroy() throws Exception {
        scheduledTask.forEach(task -> task.cancel(false)); // false = дождаться завершения текущего выполнения
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        scheduledTask.add(
                taskScheduler.scheduleWithFixedDelay(
                        outboxPoller::poll,
                        Duration.ofMillis(outboxProperties.polling().interval().toMillis())
                ));
        scheduledTask.add(
                taskScheduler.scheduleWithFixedDelay(
                        outboxMaintenance::reclaimStale,
                        Duration.ofMillis(outboxProperties.polling().inFlightTimeout().toMillis())
                ));

        if (outboxProperties.maintenance().enabled())
            scheduledTask.add(
                    taskScheduler.scheduleWithFixedDelay(
                            outboxMaintenance::purge,
                            Duration.ofMillis(outboxProperties.maintenance().interval().toMillis())
                    ));


    }
}