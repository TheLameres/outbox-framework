package io.txbox.jpa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.support.ClasspathScanningPersistenceUnitPostProcessor;

import java.util.List;

@AutoConfiguration(after = {
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
})
public class SharedJpaAutoConfiguration {

    @Bean
    public EntityManagerFactoryBuilderCustomizer txBoxEntityManagerFactoryBuilderCustomizer(
            List<TxBoxJpaPackagesCustomizer> txBoxJpaPackagesCustomizers) {
        return builder -> {
            var packages = txBoxJpaPackagesCustomizers.stream()
                    .flatMap(it -> it.customize().stream())
                    .map(ClasspathScanningPersistenceUnitPostProcessor::new)
                    .toArray(ClasspathScanningPersistenceUnitPostProcessor[]::new);
            builder.addPersistenceUnitPostProcessors(packages);
        };

    }

}