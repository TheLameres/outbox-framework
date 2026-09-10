package io.txbox.jpa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@Slf4j
public class SharedJpaAutoConfiguration {

    @Bean
    public LocalContainerEntityManagerFactoryBean txBoxEntityManagerFactoryBean(
            EntityManagerFactoryBuilder builder,
            DataSource dataSource,
            List<TxBoxJpaPackagesCustomizer> customizers,
            ApplicationContext context) {
        log.info("Calling txBoxEntityManagerFactoryBean");

        var packages = customizers.stream()
                .flatMap(it -> it.customize().stream())
                .toArray(String[]::new);
        log.info("Analyze package: {}", packages);
        return builder.dataSource(dataSource)
                .persistenceUnit("txbox-pu")
                .packages(packages)
                .build();
    }


}
