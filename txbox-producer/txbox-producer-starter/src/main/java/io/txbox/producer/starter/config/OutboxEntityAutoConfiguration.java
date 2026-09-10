package io.txbox.producer.starter.config;

import io.txbox.jpa.TxBoxJpaPackagesCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
public class OutboxEntityAutoConfiguration {
    @Bean
    public TxBoxJpaPackagesCustomizer outBoxJpaPackagesCustomizer() {
        return () -> List.of("io.txbox.producer.jpa.entity");
    }
}
