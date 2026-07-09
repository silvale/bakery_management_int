/*
 * Copyright© OneEmpower Pte Ltd. All rights reserved.
 *
 * This work contains trade secrets and confidential material of
 * OneEmpower Pte Ltd, and its unauthorised dissemination, use or
 * disclosure in whole or in part  without explicit written
 * permission of OneEmpower Pte Ltd is strictly prohibited.
 */
package com.bakery.api;

import java.util.Optional;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
@PropertySource(value = "classpath:git.properties", ignoreResourceNotFound = true)
@PropertySource(value = "classpath:META-INF/build-info.properties", ignoreResourceNotFound = true)
public class BakeryApplication {

    public static void main(String[] args) {
        var app = new SpringApplication(BakeryApplication.class);
        Environment env = app.run(args).getEnvironment();
        logStartup(env);
    }

    private static void logStartup(Environment env) {
        String protocol = Optional.ofNullable(env.getProperty("server.ssl.key-store"))
                .map(k -> "https")
                .orElse("http");
        String port = env.getProperty("server.port");
        String name = env.getProperty("spring.application.name");
        log.info(
                """

                        ----------------------------------------------------------
                        \tApplication '{}' started!
                        \tLocal: \t{}://localhost:{}
                        \tProfile(s): \t{}
                        ----------------------------------------------------------""",
                name,
                protocol,
                port,
                env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles());
    }
}
