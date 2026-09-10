package io.txbox.kafka;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;

@AutoConfiguration(after = KafkaAutoConfiguration.class)
public class TxBoxKafkaAutoConfiguration {
}
