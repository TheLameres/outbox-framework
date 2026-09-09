package io.txbox.consumer.kafka.configuration;

public interface InboxKafkaConfiguration {
    String topic();
    String groupId();
    int concurrency();
    long pollTimeoutMs();

}
