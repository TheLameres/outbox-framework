package io.txbox.producer.starter;

import io.txbox.producer.starter.OutboxProperties.Maintenance;
import io.txbox.producer.starter.OutboxProperties.Polling;

/**
 * Единственная точка входа для настроек из application.yml/properties.
 * Делегирует к OutboxProperties — здесь для обратной совместимости с
 * SpEL-выражениями типа {@code #{@outboxProperties.maintenance().retention()}}.
 *
 * <p>Зарегистрирован как бин с именем {@code outboxProperties} в OutboxAutoConfiguration.
 */
public final class OutboxPropertiesAccessor {

    private final OutboxProperties props;

    public OutboxPropertiesAccessor(OutboxProperties props) {
        this.props = props;
    }

    public Polling polling() {
        return props.polling();
    }

    public Maintenance maintenance() {
        return props.maintenance();
    }

    public OutboxProperties.Retry retry() {
        return props.retry();
    }

    public OutboxProperties.Kafka kafka() {
        return props.kafka();
    }

    public boolean enabled() {
        return props.enabled();
    }
}
