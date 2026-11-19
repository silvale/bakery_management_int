/*
 * Copyright© OneEmpower Pte Ltd. All rights reserved.
 *
 * This work contains trade secrets and confidential material of
 * OneEmpower Pte Ltd, and its unauthorised dissemination, use or
 * disclosure in whole or in part  without explicit written
 * permission of OneEmpower Pte Ltd is strictly prohibited.
 */
package com.bakery.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test tích hợp: cần DB thật nên mặc định TẮT (10/BE-1). Chạy bằng {@code -Dit=true} kèm DB tạm riêng,
 * xem {@code src/test/resources/application-test.yml}. Không bao giờ trỏ DB dev.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "it", matches = "true")
class BakeryApplicationTest {

    @Test
    void contextLoads() {}
}
