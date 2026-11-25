package com.bakery.api.auth.scope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 11/D2: endpoint chỉ dành cho user được gán ÍT NHẤT MỘT trong các kho này (theo việc của kho, không theo mã). Admin bỏ qua.
 * Đặt ở lớp (áp mọi endpoint, kể cả endpoint kế thừa từ BakeryAdminResource) hoặc ở phương thức (ghi đè lớp).
 * Không thay cho {@code @RequirePermission}: hai kiểm tra độc lập, cả hai phải qua.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireWarehouse {
    /** Phải được gán ít nhất một kho có một trong các việc này (22). */
    com.bakery.api.master.entity.WarehouseRole[] role();
}
