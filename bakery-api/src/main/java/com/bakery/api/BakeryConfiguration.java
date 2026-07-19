package com.bakery.api;

import java.time.Clock;
import java.util.Optional;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.bakery.framework.security.BakeryActorResolver;

import lombok.RequiredArgsConstructor;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@ConfigurationPropertiesScan({"com.bakery.framework", "com.bakery.api"})
@EnableJpaRepositories(basePackages = {"com.bakery.api", "com.bakery.framework"})
@EntityScan(basePackages = {"com.bakery.api", "com.bakery.framework"})
@ComponentScan(basePackages = {"com.bakery.framework", "com.bakery.api"})
@RequiredArgsConstructor
public class BakeryConfiguration {

    private final BakeryActorResolver actorResolver;

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(actorResolver.currentUserId());
    }

    /** Cấu hình Hibernate Envers: suffix bảng _HIS, lưu snapshot khi xóa. */
    @Bean
    public HibernatePropertiesCustomizer enversPropertiesCustomizer() {
        return properties -> {
            properties.put("org.hibernate.envers.audit_table_suffix", "_HIS");
            properties.put("org.hibernate.envers.store_data_at_delete", true);
            properties.put("org.hibernate.envers.revision_field_name", "REV");
            properties.put("org.hibernate.envers.revision_type_field_name", "REVTYPE");
        };
    }
}
