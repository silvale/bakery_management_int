package com.bakery.common.util;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Bật JPA Auditing cho toàn bộ project.
 * auditorAwareRef     → AuditorAwareImpl (lấy username hiện tại)
 * dateTimeProviderRef → offsetDateTimeProvider (trả OffsetDateTime
 *   thay vì LocalDateTime mặc định, khớp với BaseEntity.createdAt/updatedAt)
 */
@Configuration
@EnableJpaAuditing(
        auditorAwareRef     = "auditorAware",
        dateTimeProviderRef = "offsetDateTimeProvider"
)
@EntityScan(basePackages = "com.bakery.common.entity")
@EnableJpaRepositories(basePackages = "com.bakery.common.repository")
public class JpaConfig {

    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}