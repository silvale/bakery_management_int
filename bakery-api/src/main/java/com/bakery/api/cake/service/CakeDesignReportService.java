package com.bakery.api.cake.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.cake.dto.CakeDesignReportRow;
import com.bakery.api.cake.entity.CakeDesign;
import com.bakery.api.cake.entity.DailyReportLineDesign;
import com.bakery.api.cake.repository.DailyReportLineDesignRepository;
import com.bakery.framework.entity.DailyReportStatus;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Báo cáo mẫu bánh bán chạy / bị hủy (02/BE-6), cộng dồn từ {@code daily_report_line_design}. */
@Service
@RequiredArgsConstructor
public class CakeDesignReportService {

    private final DailyReportLineDesignRepository repository;
    private final CakeDesignService designService;

    @Transactional(readOnly = true)
    public List<CakeDesignReportRow> report(LocalDate from, LocalDate to, UUID productId, String groupCode) {
        if (from == null || to == null) throw new IllegalArgumentException("Thiếu from/to");
        if (from.isAfter(to)) throw new IllegalArgumentException("from phải trước hoặc bằng to");

        Map<UUID, CakeDesignReportRow> rows = new HashMap<>();
        Map<UUID, LocalDate> lastDate = new HashMap<>();
        Map<String, String> groups = designService.groupNameMap();
        for (DailyReportLineDesign x : repository.findFinalizedBetween(DailyReportStatus.FINALIZED, from, to)) {
            CakeDesign d = x.getCakeDesign();
            if (productId != null && !productId.equals(d.getProduct().getId())) continue;
            if (groupCode != null && !groupCode.equals(d.getGroupCode())) continue;
            CakeDesignReportRow r = rows.computeIfAbsent(d.getId(), k -> {
                CakeDesignReportRow n = new CakeDesignReportRow();
                n.setDesignId(d.getId());
                n.setDesign(new ReferenceValue(d.getCode(), d.getName()));
                n.setCode(d.getCode());
                n.setImageUrl(d.getImageUrl());
                n.setProduct(new ReferenceValue(d.getProduct().getCode(), d.getProduct().getName()));
                if (d.getGroupCode() != null) {
                    n.setGroup(new ReferenceValue(d.getGroupCode(), groups.getOrDefault(d.getGroupCode(), d.getGroupCode())));
                }
                return n;
            });
            r.setQtyReceived(r.getQtyReceived().add(nz(x.getQtyReceived())));
            r.setQtySold(r.getQtySold().add(nz(x.getQtySoldImplied())));
            r.setQtyCancelled(r.getQtyCancelled().add(nz(x.getQtyCancelled())));
            LocalDate date = x.getLine().getDailyReport().getReportDate();
            if (lastDate.get(d.getId()) == null || date.isAfter(lastDate.get(d.getId()))) {
                if (x.getQtyRemainingActual() != null) {   // báo cáo chưa nhập còn lại thì giữ số đã biết gần nhất
                    lastDate.put(d.getId(), date);
                    r.setQtyRemainingLast(x.getQtyRemainingActual());
                }
            }
        }
        return rows.values().stream()
                .sorted(Comparator.comparing(CakeDesignReportRow::getQtySold).reversed()
                        .thenComparing(CakeDesignReportRow::getCode))
                .toList();
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
