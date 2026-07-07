package com.bakery.api.modules.sales.services;

import com.bakery.api.modules.sales.dtos.DailyShopReportRequest;
import com.bakery.api.modules.sales.dtos.DailyShopReportResponse;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.sales.entities.DailyShopReport;
import com.bakery.api.modules.masterdata.entities.Product;
import com.bakery.api.framework.enums.ItemType;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.sales.repositories.DailyShopReportRepository;
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
public class DailyShopReportService {

    private final DailyShopReportRepository reportRepo;
    private final BranchRepository          branchRepo;
    private final ProductRepository         productRepo;

    /**
     * Submit / upsert báo cáo cuối ngày.
     * Nếu đã tồn tại (report_date + branch_id + item_id) thì cập nhật.
     */
    @Transactional
    public DailyShopReportResponse submit(DailyShopReportRequest req, String actor) {
        Branch branch = branchRepo.findById(req.branchId())
                .orElseThrow(() -> new IllegalArgumentException("Branch không tồn tại: " + req.branchId()));

        DailyShopReport report = reportRepo
                .findByReportDateAndBranchIdAndItemId(req.reportDate(), req.branchId(), req.itemId())
                .orElse(null);

        if (report == null) {
            report = DailyShopReport.builder()
                    .reportDate(req.reportDate())
                    .branch(branch)
                    .itemId(req.itemId())
                    .itemType(req.itemType() != null ? req.itemType() : ItemType.PRODUCT)
                    .qtyLeftoverTheoretical(req.qtyLeftoverTheoretical())
                    .qtyDestroyedActual(req.qtyDestroyedActual())
                    .submittedBy(actor)
                    .submittedAt(OffsetDateTime.now())
                    .note(req.note())
                    .build();
            log.info("[ShopReport] Tạo mới: branch={} date={} item={}", req.branchId(), req.reportDate(), req.itemId());
        } else {
            report.setQtyLeftoverTheoretical(req.qtyLeftoverTheoretical());
            report.setQtyDestroyedActual(req.qtyDestroyedActual());
            report.setSubmittedBy(actor);
            report.setSubmittedAt(OffsetDateTime.now());
            report.setNote(req.note());
            log.info("[ShopReport] Cập nhật: branch={} date={} item={}", req.branchId(), req.reportDate(), req.itemId());
        }

        report = reportRepo.save(report);
        return toResponse(report);
    }

    /**
     * Danh sách báo cáo theo ngày + branch.
     */
    public List<DailyShopReportResponse> findByDateAndBranch(LocalDate date, UUID branchId) {
        List<DailyShopReport> reports = branchId != null
                ? reportRepo.findByReportDateAndBranchId(date, branchId)
                : reportRepo.findAll().stream()
                        .filter(r -> r.getReportDate().equals(date))
                        .toList();
        return reports.stream().map(this::toResponse).toList();
    }

    private DailyShopReportResponse toResponse(DailyShopReport r) {
        Product product = productRepo.findById(r.getItemId()).orElse(null);
        BigDecimal unexplained = r.getQtyLeftoverTheoretical().subtract(r.getQtyDestroyedActual());

        return DailyShopReportResponse.builder()
                .id(r.getId())
                .reportDate(r.getReportDate())
                .branchId(r.getBranch().getId())
                .branchName(r.getBranch().getName())
                .itemId(r.getItemId())
                .itemCode(product != null ? product.getCode() : null)
                .itemName(product != null ? product.getName() : null)
                .itemType(r.getItemType())
                .qtyLeftoverTheoretical(r.getQtyLeftoverTheoretical())
                .qtyDestroyedActual(r.getQtyDestroyedActual())
                .unexplainedLoss(unexplained.compareTo(BigDecimal.ZERO) > 0 ? unexplained : BigDecimal.ZERO)
                .submittedBy(r.getSubmittedBy())
                .submittedAt(r.getSubmittedAt())
                .note(r.getNote())
                .build();
    }
}
