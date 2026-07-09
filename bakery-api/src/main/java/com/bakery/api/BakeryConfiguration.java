package com.bakery.api;

import java.time.Clock;
import java.util.Optional;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
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
}
