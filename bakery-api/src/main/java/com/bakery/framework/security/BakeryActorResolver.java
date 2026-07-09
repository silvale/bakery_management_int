package com.bakery.framework.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the current actor (userId, username) from JWT-populated SecurityContext.
 */
@Component
public class BakeryActorResolver {

    public BakeryUserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof BakeryUserPrincipal principal) {
            return principal;
        }
        return null;
    }

    public String currentUserId() {
        BakeryUserPrincipal user = currentUser();
        return user != null ? user.getUserId() : "SYSTEM";
    }

    public String currentUsername() {
        BakeryUserPrincipal user = currentUser();
        return user != null ? user.getUsername() : "SYSTEM";
    }
}
