package io.txbox.consumer.starter.config;

import io.txbox.jpa.TxBoxJpaPackagesCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
public class InboxEntityAutoConfiguration {
    @Bean
    public TxBoxJpaPackagesCustomizer inBoxJpaPackagesCustomizer() {
        return () -> List.of("io.txbox.consumer.jpa.entity");
    }
}
