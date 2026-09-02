package com.company.orders;

import org.springframework.boot.SpringApplication;

public class TestOrdersApplication {

    static void main(String[] args) {
        SpringApplication.from(OrdersApplication::main)
                .with(ContainersConfiguration.class)
                .run(args);
    }
}
