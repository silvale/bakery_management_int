package com.bakery.api.production.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.api.production.dto.ProductionPlanAdjustRequest;
import com.bakery.api.production.dto.ProductionPlanResponse;
import com.bakery.api.production.entity.ProductionPlan;
import com.bakery.api.production.service.ProductionIngredientService;
import com.bakery.api.production.service.ProductionPlanService;
import com.bakery.api.production.service.ProductionPlannerService;
import jakarta.validation.Valid;
import com.bakery.framework.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API kế hoạch sản xuất.
 *
 * <p>Manager: xem DRAFT + APPROVED, điều chỉnh, approve/reject.
 * <p>Bếp: chỉ xem danh sách APPROVED.
 */
@RestController
@RequestMapping("/api/v1/production/plans")
@RequiredArgsConstructor
@RequirePermission(screen = "PROD_PLANS", action = "VIEW")
public class ProductionPlanController {

    private final ProductionPlanService service;
    private final ProductionPlannerService plannerService;
    private final ProductionIngredientService ingredientService;

    /** Bếp lấy danh sách kế hoạch đã APPROVED. */
    @GetMapping
    public List<ProductionPlanResponse> findApproved() {
        return service.findApproved();
    }

    /** Manager xem danh sách kế hoạch DRAFT chờ duyệt. */
    @GetMapping("/drafts")
    public List<ProductionPlanResponse> findDraft() {
        return service.findDraft();
    }

    /**
     * Tạo thủ công kế hoạch DRAFT cho ngày bất kỳ (không cần DailyReport).
     * Idempotent: nếu đã tồn tại → trả về plan hiện tại.
     */
    @PostMapping("/generate")
    @RequirePermission(screen = "PROD_PLANS", action = "CREATE")
    public ProductionPlanResponse generate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ProductionPlan plan = plannerService.generateForDate(date);
        return service.findById(plan.getId());
    }

    /** Manager xem kế hoạch theo ngày (DRAFT hoặc APPROVED). */
    @GetMapping("/by-date")
    public ProductionPlanResponse findByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.findByDate(date);
    }

    @GetMapping("/{id}")
    public ProductionPlanResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    /** Manager điều chỉnh số lượng khi còn DRAFT. */
    @PutMapping("/{id}/adjust")
    @RequirePermission(screen = "PROD_PLANS", action = "CREATE")
    public ProductionPlanResponse adjust(
            @PathVariable UUID id,
            @Valid @RequestBody ProductionPlanAdjustRequest req) {
        return service.adjust(id, req);
    }

    /**
     * Manager approve → bếp thấy ngay.
     * Response bao gồm cả danh sách NL thiếu (nếu có) để UI cảnh báo.
     */
    @PostMapping("/{id}/approve")
    @RequirePermission(screen = "PROD_PLANS", action = "APPROVE")
    public java.util.Map<String, Object> approve(@PathVariable UUID id) {
        ProductionPlanResponse plan = service.approve(id);

        // Re-check NL ngay sau approve để phát hiện NL bị bỏ qua trong TRANSFER
        java.util.Map<String, Object> nlCheck = ingredientService.checkIngredients(id);
        @SuppressWarnings("unchecked")
        java.util.List<?> shortage = (java.util.List<?>) nlCheck.get("shortage");

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("plan", plan);
        result.put("nlWarning", shortage != null && !shortage.isEmpty());
        result.put("nlShortage", shortage);
        result.put("message", shortage != null && !shortage.isEmpty()
                ? "Kế hoạch đã duyệt nhưng có " + shortage.size()
                    + " NL thiếu tồn MAIN — chưa được đưa vào phiếu TRANSFER. Cần nhập kho trước."
                : "Kế hoạch đã duyệt. Phiếu SX và TRANSFER đã được tạo tự động.");
        return result;
    }

    /** Manager reject DRAFT. */
    @PostMapping("/{id}/reject")
    @RequirePermission(screen = "PROD_PLANS", action = "REJECT")
    public ProductionPlanResponse reject(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        return service.reject(id, reason);
    }

    /**
     * Tạo lại kế hoạch đã bị REJECTED — xóa lines cũ, generate DRAFT mới với tồn kho = 0.
     */
    @PostMapping("/{id}/regenerate")
    @RequirePermission(screen = "PROD_PLANS", action = "CREATE")
    public ProductionPlanResponse regenerate(@PathVariable UUID id) {
        ProductionPlan plan = plannerService.regenerateRejected(id);
        return service.findById(plan.getId());
    }

    /**
     * Xóa kế hoạch DRAFT để tạo lại từ đầu.
     * Chỉ cho phép xóa khi trạng thái là DRAFT.
     */
    @DeleteMapping("/{id}")
    @RequirePermission(screen = "PROD_PLANS", action = "CREATE")
    public ResponseEntity<Void> deleteDraft(@PathVariable UUID id) {
        service.deleteDraft(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Tạo lại phiếu SX từ plan đã APPROVED.
     * Dùng khi approve xong nhưng phiếu SX chưa được tạo (vd: items chưa có item_group).
     */
    @PostMapping("/{id}/generate-requests")
    @RequirePermission(screen = "PROD_PLANS", action = "CREATE")
    public java.util.Map<String, Object> generateRequests(@PathVariable UUID id) {
        int count = service.generateRequestsFromApprovedPlan(id);
        return java.util.Map.of("planId", id, "requestsCreated", count);
    }

    /**
     * Cập nhật plannedQty cho 1 group trong plan (chỉ khi còn DRAFT).
     * BATCH_FORMULA: plannedQty = số cối; FREE_GROUP: plannedQty = override target.
     */
    @PutMapping("/{id}/groups/{groupId}/planned-qty")
    @RequirePermission(screen = "PROD_PLANS", action = "CREATE")
    public ProductionPlanResponse updateGroupPlannedQty(
            @PathVariable UUID id,
            @PathVariable UUID groupId,
            @RequestParam int plannedQty) {
        return service.updateGroupPlannedQty(id, groupId, plannedQty);
    }

    /**
     * Kiểm tra nguyên liệu kho MAIN có đủ cho kế hoạch SX không.
     *
     * <p>Trả về:
     * - {@code allSufficient}: true nếu tất cả NL đủ
     * - {@code sufficient}: danh sách NL đủ
     * - {@code shortage}: danh sách NL thiếu (kèm số lượng cần thêm)
     */
    @GetMapping("/{id}/ingredient-check")
    public java.util.Map<String, Object> ingredientCheck(@PathVariable UUID id) {
        return ingredientService.checkIngredients(id);
    }

    /**
     * Tạo phiếu nhập kho PURCHASE vào MAIN cho các NL đang thiếu.
     * Dùng khi check NL thấy thiếu — tạo PO để nhập thêm trước khi xuất sang bếp.
     */
    @PostMapping("/{id}/generate-purchase")
    @RequirePermission(screen = "PROD_PLANS", action = "CREATE")
    public java.util.Map<String, Object> generatePurchase(@PathVariable UUID id) {
        InventoryRequest req = ingredientService.generatePurchaseRequest(id);
        return java.util.Map.of(
                "purchaseCode", req.getCode(),
                "purchaseId",   req.getId(),
                "status",       req.getApprovalStatus(),
                "lineCount",    req.getLines() != null ? req.getLines().size() : 0
        );
    }

    /**
     * Tạo phiếu xuất kho TRANSFER MAIN→KITCHEN cho kế hoạch SX.
     * Phiếu ở trạng thái PENDING_APPROVAL — cần Admin duyệt trước khi xuất.
     */
    @PostMapping("/{id}/generate-transfer")
    @RequirePermission(screen = "PROD_PLANS", action = "CREATE")
    public java.util.Map<String, Object> generateTransfer(@PathVariable UUID id) {
        InventoryRequest req = ingredientService.generateTransferRequest(id);
        return java.util.Map.of(
                "transferCode",  req.getCode(),
                "transferId",    req.getId(),
                "status",        req.getApprovalStatus(),
                "lineCount",     req.getLines() != null ? req.getLines().size() : 0
        );
    }
}
