package com.bakery.framework.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.bakery.framework.security.BakeryUserPrincipal;

/**
 * Envers RevisionListener — inject actor vào mỗi revision khi Envers tạo snapshot.
 *
 * <p>Envers tự khởi tạo class này (không qua Spring DI), nên đọc trực tiếp
 * từ {@link SecurityContextHolder} thay vì inject bean.
 */
public class BakeryRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        BakeryRevisionEntity rev = (BakeryRevisionEntity) revisionEntity;
        rev.setActor(resolveActor());
    }

    private String resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof BakeryUserPrincipal principal) {
            return principal.getUserId();
        }
        return "SYSTEM";
    }
}
