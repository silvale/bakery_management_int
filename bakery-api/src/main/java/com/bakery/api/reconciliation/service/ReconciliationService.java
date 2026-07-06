package com.bakery.api.reconciliation.service;

import com.bakery.api.reconciliation.dto.ReconciliationRowResponse;
import com.bakery.api.reconciliation.dto.ReconciliationSummaryResponse;
import com.bakery.common.entity.Branch;
import com.bakery.common.entity.Product;
import com.bakery.common.entity.ReconciliationViewEntry;
import com.bakery.common.repository.BranchRepository;
import com.bakery.common.repository.ProductRepository;
import com.bakery.common.repository.ReconciliationViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ReconciliationViewRepository reconRepo;
    private final ProductRepository            productRepo;
    private final BranchRepository             branchRepo;

    /**
     * Reconciliation 1 ngày, tuỳ chọn filter theo branch.
     */
    public ReconciliationSummaryResponse getByDate(LocalDate date, UUID branchId) {
        List<ReconciliationViewEntry> entries = branchId != null
                ? reconRepo.findByReconDateAndBranchId(date, branchId)
                : reconRepo.findByReconDate(date);

        return buildSummary(date, date, branchId, entries);
    }

    /**
     * Reconciliation khoảng ngày, tuỳ chọn filter theo branch.
     */
    public ReconciliationSummaryResponse getByDateRange(LocalDate from, LocalDate to, UUID branchId) {
        List<ReconciliationViewEntry> entries = branchId != null
                ? reconRepo.findByDateRangeAndBranch(from, to, branchId)
                : reconRepo.findByDateRange(from, to);

        return buildSummary(from, to, branchId, entries);
    }

    /**
     * Chỉ các row có chênh lệch trong 1 ngày (để alert / báo cáo nhanh).
     */
    public List<ReconciliationRowResponse> getDiscrepanciesByDate(LocalDate date) {
        List<ReconciliationViewEntry> entries = reconRepo.findDiscrepanciesByDate(date);
        Map<UUID, Product> products = loadProducts(entries);
        Map<UUID, Branch>  branches = loadBranches(entries);
        return entries.stream()
                .map(e -> toRow(e, products, branches))
                .toList();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private ReconciliationSummaryResponse buildSummary(
            LocalDate from, LocalDate to, UUID branchId,
            List<ReconciliationViewEntry> entries) {

        Map<UUID, Product> products = loadProducts(entries);
        Map<UUID, Branch>  branches = loadBranches(entries);

        List<ReconciliationRowResponse> rows = entries.stream()
                .map(e -> toRow(e, products, branches))
                .toList();

        long okCount          = rows.stream().filter(r -> "OK".equals(r.getStatus())).count();
        long discrepancyCount = rows.stream().filter(r -> "DISCREPANCY".equals(r.getStatus())).count();
        long missingCount     = rows.stream().filter(r -> "MISSING_DATA".equals(r.getStatus())).count();

        BigDecimal totalPos = rows.stream()
                .map(ReconciliationRowResponse::getVariance)
                .filter(v -> v != null && v.compareTo(ZERO) > 0)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalNeg = rows.stream()
                .map(ReconciliationRowResponse::getVariance)
                .filter(v -> v != null && v.compareTo(ZERO) < 0)
                .reduce(ZERO, BigDecimal::add);

        Branch branch = branchId != null ? branchRepo.findById(branchId).orElse(null) : null;

        return ReconciliationSummaryResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .branchId(branchId)
                .branchName(branch != null ? branch.getName() : null)
                .totalRows((int) rows.size())
                .okCount((int) okCount)
                .discrepancyCount((int) discrepancyCount)
                .missingDataCount((int) missingCount)
                .totalPositiveVariance(totalPos)
                .totalNegativeVariance(totalNeg)
                .rows(rows)
                .build();
    }

    private ReconciliationRowResponse toRow(
            ReconciliationViewEntry e,
            Map<UUID, Product> products,
            Map<UUID, Branch>  branches) {

        BigDecimal bep       = nvl(e.getQtyBepDelivered());
        BigDecimal pos       = nvl(e.getQtyPosSold());
        BigDecimal destroyed = nvl(e.getQtyDestroyed());
        BigDecimal variance  = nvl(e.getVariance());

        // Classify
        boolean missingBep  = bep.compareTo(ZERO) == 0;
        boolean missingPos  = pos.compareTo(ZERO) == 0;
        boolean missingShop = destroyed.compareTo(ZERO) == 0 && bep.compareTo(ZERO) == 0 && pos.compareTo(ZERO) == 0;

        String status;
        String note = null;

        if (missingBep && missingPos) {
            status = "MISSING_DATA";
            note   = "Không có dữ liệu từ Bếp hoặc POS";
        } else if (variance.compareTo(ZERO) != 0) {
            status = "DISCREPANCY";
            if (variance.compareTo(ZERO) > 0) {
                note = String.format("Shop tiêu thụ nhiều hơn Bếp giao %s đơn vị", variance.toPlainString());
            } else {
                note = String.format("Bếp giao nhiều hơn Shop ghi nhận %s đơn vị", variance.abs().toPlainString());
            }
        } else {
            status = "OK";
        }

        Product product = products.get(e.getItemId());
        Branch  branch  = branches.get(e.getBranchId());

        return ReconciliationRowResponse.builder()
                .itemId(e.getItemId())
                .itemCode(product != null ? product.getCode() : null)
                .itemName(product != null ? product.getName() : null)
                .reconDate(e.getReconDate())
                .branchId(e.getBranchId())
                .branchName(branch != null ? branch.getName() : null)
                .qtyBepDelivered(bep)
                .qtyPosSold(pos)
                .qtyDestroyed(destroyed)
                .variance(variance)
                .status(status)
                .note(note)
                .build();
    }

    /** Batch-load products để tránh N+1 */
    private Map<UUID, Product> loadProducts(List<ReconciliationViewEntry> entries) {
        List<UUID> ids = entries.stream().map(ReconciliationViewEntry::getItemId).distinct().toList();
        return productRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /** Batch-load branches để tránh N+1 */
    private Map<UUID, Branch> loadBranches(List<ReconciliationViewEntry> entries) {
        List<UUID> ids = entries.stream().map(ReconciliationViewEntry::getBranchId).distinct().toList();
        return branchRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : ZERO;
    }
}
