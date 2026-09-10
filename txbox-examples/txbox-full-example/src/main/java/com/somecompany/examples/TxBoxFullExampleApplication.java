package com.somecompany.examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class TxBoxFullExampleApplication {
    static void main(String[] args) {
        SpringApplication.run(TxBoxFullExampleApplication.class, args);
    }
}
