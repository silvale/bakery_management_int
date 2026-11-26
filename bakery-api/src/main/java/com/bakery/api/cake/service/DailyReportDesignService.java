package com.bakery.api.cake.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.cake.dto.DailyReportLineDesignResponse;
import com.bakery.api.cake.dto.SubmitLineBody;
import com.bakery.api.cake.entity.CakeDesign;
import com.bakery.api.cake.entity.DailyReportLineDesign;
import com.bakery.api.cake.entity.DeliveryRecordDesign;
import com.bakery.api.cake.repository.CakeDesignRepository;
import com.bakery.api.cake.repository.DailyReportLineDesignRepository;
import com.bakery.api.cake.repository.DeliveryRecordDesignRepository;
import com.bakery.api.report.entity.DailyReport;
import com.bakery.api.report.entity.DailyReportLine;
import com.bakery.api.report.repository.DailyReportRepository;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Báo cáo ngày theo mẫu bánh (02/BE-4): tồn đầu, nhận, còn lại, hủy, bán suy ra cho từng mẫu.
 * Bán suy ra dùng cùng công thức dòng thành phẩm: {@code tồn đầu + nhận − còn lại}.
 */
@Service
@RequiredArgsConstructor
public class DailyReportDesignService {

    private final CakeDesignRepository designRepository;
    private final DailyReportRepository reportRepository;
    private final DailyReportLineDesignRepository lineDesignRepository;
    private final DeliveryRecordDesignRepository deliveryDesignRepository;

    /** Số liệu một mẫu trong ngày. {@code remaining}/{@code cancelled}/{@code soldImplied} null khi chưa nhập. */
    public record DesignRow(CakeDesign design, BigDecimal opening, BigDecimal received,
            BigDecimal remaining, BigDecimal cancelled, BigDecimal soldImplied) {}

    /**
     * Số bán suy ra = tồn đầu + nhận − còn lại − hủy (D7 mới: hủy là mất, không phải bán). null khi chưa nhập
     * "còn lại"; hủy null coi là 0. Dùng chung cho dòng thành phẩm ({@code DailyReportService.finalize}) và mẫu.
     */
    public static BigDecimal soldImplied(BigDecimal opening, BigDecimal received, BigDecimal remaining,
            BigDecimal cancelled) {
        if (remaining == null) return null;
        return nz(opening).add(nz(received)).subtract(remaining).subtract(nz(cancelled));
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    // ── Đọc ──────────────────────────────────────────────────

    /** Trong {@code ids}, các thành phẩm có ít nhất 1 mẫu (kể cả đã ngừng dùng). */
    public List<UUID> designedProductIdsAmong(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : designRepository.findProductIdsHavingDesign(ids);
    }

    public List<UUID> allDesignedProductIds() { return designRepository.findAllDesignedProductIds(); }

    /**
     * Báo cáo DRAFT: tính ngay lúc đọc. Trả productId → các mẫu có tồn đầu &gt; 0, hoặc nhận &gt; 0,
     * hoặc đã nhập số (kể cả mẫu đã ngừng dùng).
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<DesignRow>> compute(DailyReport report, Collection<UUID> productIds) {
        if (productIds.isEmpty()) return Map.of();
        LocalDate date = report.getReportDate();

        Map<UUID, BigDecimal> opening = new HashMap<>();
        reportRepository.findFirstByReportDateLessThanOrderByReportDateDesc(date).ifPresent(prev ->
                lineDesignRepository.findByReportId(prev.getId()).forEach(x -> {
                    if (x.getQtyRemainingActual() != null) {
                        opening.put(x.getCakeDesign().getId(), x.getQtyRemainingActual());
                    }
                }));

        Map<UUID, BigDecimal> received = new HashMap<>();
        for (DeliveryRecordDesign x : deliveryDesignRepository.findByProductionDate(date)) {
            if (x.getQtyReceived() != null) {
                received.merge(x.getCakeDesign().getId(), x.getQtyReceived(), BigDecimal::add);
            }
        }

        Map<UUID, DailyReportLineDesign> entered = new HashMap<>();
        lineDesignRepository.findByReportId(report.getId()).forEach(x -> entered.put(x.getCakeDesign().getId(), x));

        Map<UUID, List<DesignRow>> out = new LinkedHashMap<>();
        designRepository.findByProductIds(productIds).stream()
                .sorted(Comparator.comparingInt(CakeDesign::getSortOrder).thenComparing(CakeDesign::getCode))
                .forEach(d -> {
                    BigDecimal o = opening.getOrDefault(d.getId(), BigDecimal.ZERO);
                    BigDecimal r = received.getOrDefault(d.getId(), BigDecimal.ZERO);
                    DailyReportLineDesign e = entered.get(d.getId());
                    if (o.signum() <= 0 && r.signum() <= 0 && e == null) return;
                    BigDecimal rem = e != null ? e.getQtyRemainingActual() : null;
                    BigDecimal canc = e != null ? e.getQtyCancelled() : null;
                    out.computeIfAbsent(d.getProduct().getId(), k -> new ArrayList<>())
                            .add(new DesignRow(d, o, r, rem, canc, soldImplied(o, r, rem, canc)));
                });
        return out;
    }

    /** Báo cáo FINALIZED: đọc nguyên từ {@code daily_report_line_design}. productId → mẫu. */
    @Transactional(readOnly = true)
    public Map<UUID, List<DesignRow>> stored(DailyReport report) {
        Map<UUID, List<DesignRow>> out = new LinkedHashMap<>();
        lineDesignRepository.findByReportId(report.getId()).stream()
                .sorted(Comparator.comparingInt((DailyReportLineDesign x) -> x.getCakeDesign().getSortOrder())
                        .thenComparing(x -> x.getCakeDesign().getCode()))
                .forEach(x -> out.computeIfAbsent(x.getLine().getItem().getId(), k -> new ArrayList<>())
                        .add(new DesignRow(x.getCakeDesign(), x.getQtyOpening(), x.getQtyReceived(),
                                x.getQtyRemainingActual(), x.getQtyCancelled(), x.getQtySoldImplied())));
        return out;
    }

    public static DailyReportLineDesignResponse toResponse(DesignRow r) {
        DailyReportLineDesignResponse d = new DailyReportLineDesignResponse();
        d.setDesignId(r.design().getId());
        d.setDesign(new ReferenceValue(r.design().getCode(), r.design().getName()));
        d.setImageUrl(r.design().getImageUrl());
        d.setGroupCode(r.design().getGroupCode());
        d.setQtyOpening(r.opening());
        d.setQtyReceived(r.received());
        d.setQtyRemainingActual(r.remaining());
        d.setQtyCancelled(r.cancelled());
        d.setQtySoldImplied(r.soldImplied());
        return d;
    }

    // ── Ghi: nhân viên nhập còn lại / hủy theo mẫu ───────────

    /**
     * Upsert số còn lại/hủy theo mẫu của một dòng thành phẩm (đã có id) và trả tổng
     * {còn lại, hủy} của mọi mẫu đã lưu để gán lên dòng thành phẩm.
     */
    @Transactional
    public BigDecimal[] submit(DailyReportLine line, UUID productId, List<SubmitLineBody.DesignRemaining> designs) {
        if (designs == null || designs.isEmpty()) {
            throw new IllegalArgumentException("Thành phẩm có mẫu: vui lòng nhập số còn lại theo từng mẫu");
        }
        Map<UUID, CakeDesign> ofProduct = designRepository.findAllByProduct(productId).stream()
                .collect(Collectors.toMap(CakeDesign::getId, d -> d));
        Set<UUID> seen = new HashSet<>();
        for (SubmitLineBody.DesignRemaining d : designs) {
            if (d == null || d.designId() == null) throw new IllegalArgumentException("Thiếu mẫu bánh");
            if (!ofProduct.containsKey(d.designId())) {
                throw new IllegalArgumentException(DesignSplit.notAllowedMessage(d.designId(),
                        id -> designRepository.findById(id).map(CakeDesign::getCode).orElse(null)));
            }
            if (!seen.add(d.designId())) throw new IllegalArgumentException("Mẫu bị nhập trùng");
            if (d.qtyRemaining() == null) throw new IllegalArgumentException("Thiếu số còn lại của mẫu");
            if (d.qtyRemaining().signum() < 0 || (d.qtyCancelled() != null && d.qtyCancelled().signum() < 0)) {
                throw new IllegalArgumentException("Số lượng không được âm");
            }
        }
        for (SubmitLineBody.DesignRemaining d : designs) {
            DailyReportLineDesign row = lineDesignRepository.findByLineAndDesign(line.getId(), d.designId())
                    .orElseGet(() -> {
                        DailyReportLineDesign n = new DailyReportLineDesign();
                        n.setLine(line);
                        n.setCakeDesign(ofProduct.get(d.designId()));
                        return n;
                    });
            row.setQtyRemainingActual(d.qtyRemaining());
            row.setQtyCancelled(d.qtyCancelled() != null ? d.qtyCancelled() : BigDecimal.ZERO);
            lineDesignRepository.save(row);
        }
        lineDesignRepository.flush();
        BigDecimal remaining = BigDecimal.ZERO, cancelled = BigDecimal.ZERO;
        for (DailyReportLineDesign x : lineDesignRepository.findByLineIds(List.of(line.getId()))) {
            remaining = remaining.add(nz(x.getQtyRemainingActual()));
            cancelled = cancelled.add(nz(x.getQtyCancelled()));
        }
        return new BigDecimal[] {remaining, cancelled};
    }

    // ── Chốt ngày ────────────────────────────────────────────

    /** Ghi snapshot các mẫu của một dòng thành phẩm khi chốt ngày (upsert theo line + mẫu). */
    @Transactional
    public void snapshot(DailyReportLine line, List<DesignRow> rows) {
        for (DesignRow r : rows) {
            DailyReportLineDesign x = lineDesignRepository.findByLineAndDesign(line.getId(), r.design().getId())
                    .orElseGet(() -> {
                        DailyReportLineDesign n = new DailyReportLineDesign();
                        n.setLine(line);
                        n.setCakeDesign(r.design());
                        return n;
                    });
            x.setQtyOpening(r.opening());
            x.setQtyReceived(r.received());
            x.setQtyRemainingActual(r.remaining());
            x.setQtyCancelled(r.cancelled());
            x.setQtySoldImplied(r.soldImplied());
            lineDesignRepository.save(x);
        }
    }
}
