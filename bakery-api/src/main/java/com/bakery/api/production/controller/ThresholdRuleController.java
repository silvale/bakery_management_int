package com.bakery.api.production.controller;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.dto.ThresholdRuleRequest;
import com.bakery.api.production.entity.ProductionThresholdRule;
import com.bakery.api.production.service.ThresholdRuleService;
import jakarta.validation.Valid;
import com.bakery.framework.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý threshold rules của sản phẩm (Pattern 1: SIMPLE).
 * Endpoint gắn vào /api/v1/items/{itemId}/threshold-rules.
 */
@RestController
@RequestMapping("/api/v1/items/{itemId}/threshold-rules")
@RequiredArgsConstructor
@RequirePermission(screen = "THRESHOLD_RULES", action = "VIEW")
public class ThresholdRuleController {

    private final ThresholdRuleService service;

    @GetMapping
    public List<ProductionThresholdRule> findByItem(@PathVariable UUID itemId) {
        return service.findByItem(itemId);
    }

    /**
     * Replace toàn bộ rules của item.
     * Gửi danh sách đầy đủ (cả WEEKDAY và WEEKEND) trong 1 request.
     */
    @PutMapping
    @RequirePermission(screen = "THRESHOLD_RULES", action = "UPDATE")
    public List<ProductionThresholdRule> replaceRules(
            @PathVariable UUID itemId,
            @Valid @RequestBody ThresholdRuleRequest req) {
        return service.replaceRules(itemId, req);
    }
}
