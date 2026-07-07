package com.bakery.api.modules.masterdata.controllers;

import com.bakery.api.framework.controllers.AdminBaseResource;
import com.bakery.api.framework.enums.BranchType;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.masterdata.BranchCommandService;
import com.bakery.api.modules.masterdata.BranchSupportService;
import com.bakery.api.modules.masterdata.dtos.BranchFilter;
import com.bakery.api.modules.masterdata.dtos.BranchRequest;
import com.bakery.api.modules.masterdata.dtos.BranchResponse;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/branches")
@RequiredArgsConstructor
@Tag(name = "Admin - Branches", description = "Quản lý chi nhánh / kho (approval workflow)")
public class BranchAdminController
        extends AdminBaseResource<BranchRequest, BranchResponse, Branch, BranchFilter> {

    private final BranchSupportService supportService;
    private final BranchCommandService commandService;
    private final EntityHistoryService historyService;
    private final BranchRepository branchRepository;

    @Override
    protected AdminEntitySupportService<BranchRequest, BranchResponse, Branch> abstractSupport() {
        return supportService;
    }

    @Override
    protected AdminCommandService<BranchRequest, BranchResponse, Branch> abstractCommand() {
        return commandService;
    }

    @Override
    protected EntityHistoryService abstractHistory() { return historyService; }

    // ── Shortcut endpoints ───────────────────────────────────

    @GetMapping("/kho-bep")
    @Operation(summary = "Danh sách Kho Bếp (KHO_BEP)")
    public List<BranchResponse> listKhoBep() {
        return branchRepository.findAllByBranchType(BranchType.KHO_BEP)
                .stream().map(supportService::toResponse).toList();
    }

    @GetMapping("/kho-tong")
    @Operation(summary = "Danh sách Kho Tổng (KHO_TONG)")
    public List<BranchResponse> listKhoTong() {
        return branchRepository.findAllByBranchType(BranchType.KHO_TONG)
                .stream().map(supportService::toResponse).toList();
    }

    @GetMapping("/shops")
    @Operation(summary = "Danh sách Shop")
    public List<BranchResponse> listShops() {
        return branchRepository.findAllByBranchType(BranchType.SHOP)
                .stream().map(supportService::toResponse).toList();
    }
}
