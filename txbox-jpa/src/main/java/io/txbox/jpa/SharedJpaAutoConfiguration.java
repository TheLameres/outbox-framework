package io.txbox.jpa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration(after = {
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
})
@Slf4j
@EnableJpaRepositories(
        basePackages = {
                "io.txbox.producer.jpa.repository",
                "io.txbox.consumer.jpa.repository"
        },
        entityManagerFactoryRef = "txBoxEntityManagerFactoryBean",
        transactionManagerRef = "txBoxTransactionalManager",
        bootstrapMode = BootstrapMode.LAZY
)
public class SharedJpaAutoConfiguration {

    /**
     * EntityManagerFactory для txbox-внутренних сущностей (inbox/outbox таблицы).
     * Использует пакеты, собранные через TxBoxJpaPackagesCustomizer.
     */
    @Bean(name = "txBoxEntityManagerFactoryBean")
    public LocalContainerEntityManagerFactoryBean txBoxEntityManagerFactoryBean(
            EntityManagerFactoryBuilder builder,
            DataSource dataSource,
            List<TxBoxJpaPackagesCustomizer> customizers) {

        var packages = customizers.stream()
                .flatMap(c -> c.customize().stream())
                .toArray(String[]::new);
        var build = builder.dataSource(dataSource)
                .persistenceUnit("txbox-pu")
                .jta(false)
                .packages(packages)
                .build();
        build.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        return build;
    }

    @Bean(name = "txBoxTransactionalManager")
    PlatformTransactionManager txBoxTransactionalManager(LocalContainerEntityManagerFactoryBean txBoxEntityManagerFactoryBean) {
        assert txBoxEntityManagerFactoryBean.getObject() != null;
        return new JpaTransactionManager(txBoxEntityManagerFactoryBean.getObject());
    }
}