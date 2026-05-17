package com.bakery.common.util;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cung cấp username hiện tại cho Spring Data JPA Auditing.
 * Dùng cho createdBy / updatedBy.
 *
 * - Nếu có Authentication (user đã login) → lấy username
 * - Nếu không (batch job chạy tự động)  → trả về 'SYSTEM'
 */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<String> {

    private static final String SYSTEM_USER = "SYSTEM";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.of(SYSTEM_USER);
        }

        return Optional.of(auth.getName());
    }
}
