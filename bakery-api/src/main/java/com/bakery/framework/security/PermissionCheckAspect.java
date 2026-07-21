package com.bakery.framework.security;

import com.bakery.api.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

/**
 * AOP aspect kiểm tra {@link RequirePermission} trước khi thực thi endpoint.
 *
 * <p>Thứ tự ưu tiên: method annotation > class annotation.
 * Nếu không tìm thấy annotation → cho phép tiếp tục (không block).
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionCheckAspect {

    private final PermissionService permissionService;

    @Around("@within(com.bakery.framework.security.RequirePermission) || @annotation(com.bakery.framework.security.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint pjp) throws Throwable {
        RequirePermission annotation = resolveAnnotation(pjp);

        if (annotation == null) {
            return pjp.proceed();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Chưa đăng nhập
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof BakeryUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }

        String roleCode = principal.getRole();
        String screenCode = annotation.screen();
        String actionCode = annotation.action();

        if (!permissionService.hasPermission(roleCode, screenCode, actionCode)) {
            log.warn("Permission denied: user={} role={} screen={} action={}",
                    principal.getUsername(), roleCode, screenCode, actionCode);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Không có quyền thực hiện thao tác này [" + screenCode + ":" + actionCode + "]");
        }

        return pjp.proceed();
    }

    private RequirePermission resolveAnnotation(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();

        // Method-level takes priority
        RequirePermission ann = method.getAnnotation(RequirePermission.class);
        if (ann != null) return ann;

        // Fall back to class-level
        return pjp.getTarget().getClass().getAnnotation(RequirePermission.class);
    }
}
