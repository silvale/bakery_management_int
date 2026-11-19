package com.bakery.api.inventory.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.bakery.api.inventory.dto.AdjustmentReportResponse;
import com.bakery.api.inventory.dto.AdjustmentReportRow;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 08/BE-3: thống kê điều chỉnh tồn. Chỉ phiếu ADJUSTMENT đã APPROVED, tháng theo {@code approved_at}
 * (giờ Asia/Ho_Chi_Minh; so sánh trên timestamptz để dùng index {@code idx_inv_req_type_approved}).
 */
@Service
@RequiredArgsConstructor
public class AdjustmentReportService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final ReferenceValue NO_REASON = new ReferenceValue("", "Chưa ghi lý do");

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public AdjustmentReportResponse summary(String month, UUID warehouseId, UUID itemId, String reasonCode) {
        YearMonth ym;
        try {
            if (month == null || month.isBlank()) throw new IllegalArgumentException("month là bắt buộc (yyyy-MM)");
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("month phải có dạng yyyy-MM: " + month);
        }
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", OffsetDateTime.ofInstant(ym.atDay(1).atStartOfDay(VN).toInstant(), VN))
                .addValue("to", OffsetDateTime.ofInstant(ym.plusMonths(1).atDay(1).atStartOfDay(VN).toInstant(), VN));

        StringBuilder sql = new StringBuilder("""
                SELECT w.id AS wid, w.code AS wcode, w.name AS wname,
                       i.id AS iid, i.code AS icode, i.name AS iname, i.image_url, l.unit,
                       l.reason_code, cv.name AS reason_name,
                       sum(CASE WHEN l.quantity > 0 THEN l.quantity ELSE 0 END)  AS qty_inc,
                       sum(CASE WHEN l.quantity < 0 THEN -l.quantity ELSE 0 END) AS qty_dec,
                       sum(CASE WHEN l.quantity > 0 THEN coalesce(l.amount, 0) ELSE 0 END)  AS amt_inc,
                       sum(CASE WHEN l.quantity < 0 THEN -coalesce(l.amount, 0) ELSE 0 END) AS amt_dec,
                       count(DISTINCT r.id) AS cnt
                  FROM inventory_request r
                  JOIN inventory_request_line l ON l.inventory_request_id = r.id
                  JOIN item i ON i.id = l.item_id
                  JOIN warehouse w ON w.id = r.target_warehouse_id
                  LEFT JOIN code_value cv ON cv.group_key = 'ADJUSTMENT_REASON' AND cv.code = l.reason_code
                 WHERE r.request_type = 'ADJUSTMENT' AND r.approval_status = 'APPROVED'
                   AND r.approved_at >= :from AND r.approved_at < :to
                   AND l.quantity <> 0
                """);
        if (warehouseId != null) { sql.append(" AND r.target_warehouse_id = :wh"); p.addValue("wh", warehouseId); }
        if (itemId != null)      { sql.append(" AND l.item_id = :item");           p.addValue("item", itemId); }
        if (reasonCode != null && !reasonCode.isBlank()) { sql.append(" AND l.reason_code = :reason"); p.addValue("reason", reasonCode); }
        sql.append(" GROUP BY w.id, w.code, w.name, i.id, i.code, i.name, i.image_url, l.unit, l.reason_code, cv.name");

        java.text.Collator vi = java.text.Collator.getInstance(Locale.forLanguageTag("vi"));
        List<AdjustmentReportRow> rows = jdbc.query(sql.toString(), p, (rs, n) -> row(rs)).stream()
                .sorted(Comparator.comparing((AdjustmentReportRow r) -> r.warehouse().name(), vi)
                        .thenComparing(r -> r.item().name(), vi)
                        .thenComparing(r -> r.reason().name(), vi)
                        .thenComparing(AdjustmentReportRow::unit))
                .toList();

        // Số phiếu khác nhau sau khi lọc (cùng điều kiện với bảng trên)
        StringBuilder cnt = new StringBuilder("""
                SELECT count(DISTINCT r.id)
                  FROM inventory_request r
                  JOIN inventory_request_line l ON l.inventory_request_id = r.id
                 WHERE r.request_type = 'ADJUSTMENT' AND r.approval_status = 'APPROVED'
                   AND r.approved_at >= :from AND r.approved_at < :to
                   AND l.quantity <> 0
                """);
        if (warehouseId != null) cnt.append(" AND r.target_warehouse_id = :wh");
        if (itemId != null) cnt.append(" AND l.item_id = :item");
        if (reasonCode != null && !reasonCode.isBlank()) cnt.append(" AND l.reason_code = :reason");
        Long requestCount = jdbc.queryForObject(cnt.toString(), p, Long.class);
        return new AdjustmentReportResponse(rows, requestCount == null ? 0 : requestCount);
    }

    private static AdjustmentReportRow row(ResultSet rs) throws SQLException {
        String reason = rs.getString("reason_code");
        return new AdjustmentReportRow(
                rs.getObject("wid", UUID.class), new ReferenceValue(rs.getString("wcode"), rs.getString("wname")),
                rs.getObject("iid", UUID.class), new ReferenceValue(rs.getString("icode"), rs.getString("iname")),
                rs.getString("image_url"), rs.getString("unit"),
                reason, reason == null ? NO_REASON : new ReferenceValue(reason,
                        rs.getString("reason_name") != null ? rs.getString("reason_name") : reason),
                rs.getBigDecimal("qty_inc"), rs.getBigDecimal("qty_dec"),
                rs.getBigDecimal("amt_inc"), rs.getBigDecimal("amt_dec"), rs.getLong("cnt"));
    }
}
