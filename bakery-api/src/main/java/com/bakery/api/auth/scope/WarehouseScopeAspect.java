package com.bakery.api.auth.scope;

import java.lang.reflect.Method;

import com.bakery.api.auth.service.AccessScope;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/** Thực thi {@link RequireWarehouse}. {@code @target} để cũng bắt phương thức kế thừa từ lớp cha của controller. */
@Aspect
@Component
@RequiredArgsConstructor
public class WarehouseScopeAspect {

    private final AccessScope scope;

    /**
     * {@code @target} không xác định được lúc khởi động nên Spring sẽ proxy MỌI bean — làm hỏng cả bean của Spring Boot
     * (lỗi khởi động Tomcat). Giới hạn bằng {@code within} vào các controller của ứng dụng.
     */
    @Around("(@target(com.bakery.api.auth.scope.RequireWarehouse) && within(com.bakery.api..controller..*))"
            + " || @annotation(com.bakery.api.auth.scope.RequireWarehouse)")
    public Object check(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        RequireWarehouse ann = method.getAnnotation(RequireWarehouse.class);
        if (ann == null) {
            // phương thức kế thừa/ghi đè: tìm trên lớp thật của đối tượng
            try {
                ann = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes())
                        .getAnnotation(RequireWarehouse.class);
            } catch (NoSuchMethodException ignored) { /* dùng lớp */ }
        }
        if (ann == null) ann = pjp.getTarget().getClass().getAnnotation(RequireWarehouse.class);
        if (ann != null) scope.assertAnyWarehouseCode(ann.any());
        return pjp.proceed();
    }
}
