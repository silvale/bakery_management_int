package com.bakery.api.modules.sales.services;

import com.bakery.api.modules.sales.dtos.PosSalesDataRequest;
import com.bakery.api.modules.sales.dtos.PosSalesDataResponse;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.sales.entities.PosSalesData;
import com.bakery.api.modules.masterdata.entities.Product;
import com.bakery.api.framework.enums.ItemType;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.sales.repositories.PosSalesDataRepository;
import com.bakery.api.modules.masterdata.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosSalesDataService {

    private final PosSalesDataRepository posRepo;
    private final BranchRepository       branchRepo;
    private final ProductRepository      productRepo;

    /**
     * Upsert 1 dòng POS (idempotent — an toàn khi re-upload file).
     */
    @Transactional
    public PosSalesDataResponse upsert(PosSalesDataRequest req, String actor) {
        Branch branch = branchRepo.findById(req.branchId())
                .orElseThrow(() -> new IllegalArgumentException("Branch không tồn tại: " + req.branchId()));

        PosSalesData row = posRepo
                .findBySalesDateAndBranchIdAndItemId(req.salesDate(), req.branchId(), req.itemId())
                .orElse(null);

        if (row == null) {
            row = PosSalesData.builder()
                    .salesDate(req.salesDate())
                    .branch(branch)
                    .itemId(req.itemId())
                    .itemType(req.itemType() != null ? req.itemType() : ItemType.PRODUCT)
                    .qtySoldPos(req.qtySoldPos())
                    .revenue(req.revenue() != null ? req.revenue() : BigDecimal.ZERO)
                    .uploadedBy(actor)
                    .uploadedAt(OffsetDateTime.now())
                    .build();
            log.info("[POS] Insert: branch={} date={} item={}", req.branchId(), req.salesDate(), req.itemId());
        } else {
            row.setQtySoldPos(req.qtySoldPos());
            row.setRevenue(req.revenue() != null ? req.revenue() : BigDecimal.ZERO);
            row.setUploadedBy(actor);
            row.setUploadedAt(OffsetDateTime.now());
            log.info("[POS] Update: branch={} date={} item={}", req.branchId(), req.salesDate(), req.itemId());
        }

        row = posRepo.save(row);
        return toResponse(row);
    }

    /**
     * Batch upsert — upload cả file POS 1 lần.
     */
    @Transactional
    public List<PosSalesDataResponse> upsertBatch(List<PosSalesDataRequest> requests, String actor) {
        return requests.stream()
                .map(req -> upsert(req, actor))
                .toList();
    }

    /**
     * Danh sách POS data theo ngày + branch.
     */
    public List<PosSalesDataResponse> findByDateAndBranch(LocalDate date, UUID branchId) {
        List<PosSalesData> rows = branchId != null
                ? posRepo.findBySalesDateAndBranchId(date, branchId)
                : posRepo.findAll().stream()
                        .filter(r -> r.getSalesDate().equals(date))
                        .toList();
        return rows.stream().map(this::toResponse).toList();
    }

    private PosSalesDataResponse toResponse(PosSalesData r) {
        Product product = productRepo.findById(r.getItemId()).orElse(null);
        return PosSalesDataResponse.builder()
                .id(r.getId())
                .salesDate(r.getSalesDate())
                .branchId(r.getBranch().getId())
                .branchName(r.getBranch().getName())
                .itemId(r.getItemId())
                .itemCode(product != null ? product.getCode() : null)
                .itemName(product != null ? product.getName() : null)
                .itemType(r.getItemType())
                .qtySoldPos(r.getQtySoldPos())
                .revenue(r.getRevenue())
                .uploadedBy(r.getUploadedBy())
                .uploadedAt(r.getUploadedAt())
                .build();
    }
}
