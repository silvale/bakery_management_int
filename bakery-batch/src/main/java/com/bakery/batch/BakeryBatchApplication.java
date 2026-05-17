package com.bakery.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.bakery.batch", "com.bakery.common"})
public class BakeryBatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(BakeryBatchApplication.class, args);
    }
}
