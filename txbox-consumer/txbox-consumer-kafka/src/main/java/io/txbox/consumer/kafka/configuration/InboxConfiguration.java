package io.txbox.consumer.kafka.configuration;

import java.util.List;

public interface InboxConfiguration {
    InboxKafkaConfiguration kafka();
    List<String> domainEventBasePackages();
}

