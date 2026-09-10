package com.somecompany.examples;

import com.somecompany.examples.configuration.TestContainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TxBoxFullExampleTestApplication {
    static void main(String[] args) {
        SpringApplication
                .from(TxBoxFullExampleApplication::main)
                .with(TestContainersConfiguration.class)
                .run(args);
    }
}
