package com.bakery.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * JPA Auditing, EntityScan, EnableJpaRepositories
 * đã được config trong JpaConfig (bakery-common).
 * Ở đây chỉ cần @ComponentScan để Spring tìm thấy
 * các beans từ tất cả module.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.bakery.api",
    "com.bakery.batch",
    "com.bakery.common"
})
public class BakeryApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(BakeryApiApplication.class, args);
    }
}
