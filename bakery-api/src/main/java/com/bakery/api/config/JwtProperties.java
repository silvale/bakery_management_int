package com.bakery.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bakery.jwt")
@Getter
@Setter
public class JwtProperties {

    /** Secret key dùng để sign HS256 token */
    private String secret;

    /** Thời gian sống của access token (phút) */
    private int accessTokenExpiryMinutes = 60;

    /** Thời gian sống của refresh token (ngày) */
    private int refreshTokenExpiryDays = 30;
}
