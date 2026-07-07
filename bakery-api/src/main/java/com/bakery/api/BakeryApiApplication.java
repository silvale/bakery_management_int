package com.bakery.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/**
 * Single-module Spring Boot application — Feature-Folder Layers.
 * @SpringBootApplication tự scan com.bakery.api.** — không cần @ComponentScan thêm.
 */
@SpringBootApplication
public class BakeryApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(BakeryApiApplication.class, args);
    }
}
