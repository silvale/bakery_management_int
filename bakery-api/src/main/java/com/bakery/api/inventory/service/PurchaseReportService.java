package com.bakery.api.inventory.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

import com.bakery.api.inventory.dto.PurchaseReportRows.Packaging;
import com.bakery.api.inventory.dto.PurchaseReportRows.Shortage;
import com.bakery.api.inventory.dto.PurchaseReportRows.Summary;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 06: báo cáo nhập hàng theo thực nhận. Chỉ phiếu PURCHASE đã APPROVED; tháng theo {@code received_date}
 * (index {@code idx_inv_req_type_received}). Số lượng = received_qty (đơn vị line.unit); tiền tính như
 * {@code InventoryRequestService.lineTotal}: unit_cost × qty, chia qty_per_pack nếu dòng có quy cách.
 */
@Service
@RequiredArgsConstructor
public class PurchaseReportService {

    private static final ReferenceValue NO_SUPPLIER = new ReferenceValue("", "Không có NCC");
    /** unit_cost × qty [/ qty_per_pack], làm tròn 2 số lẻ từng dòng như totalCost. */
    private static final String AMOUNT = "round(coalesce(l.unit_cost, 0) * %s / coalesce(nullif(p.qty_per_pack, 0), 1), 2)";

    private static final String FROM = """
            FROM inventory_request r
            JOIN inventory_request_line l ON l.inventory_request_id = r.id
            JOIN item i ON i.id = l.item_id
            LEFT JOIN supplier s ON s.id = r.supplier_id
            LEFT JOIN item_packaging p ON p.id = l.packaging_id
            """;
    private static final String WHERE = """
            WHERE r.request_type = 'PURCHASE' AND r.approval_status = 'APPROVED'
              AND r.received_date >= :from AND r.received_date < :to
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<Summary> summary(String month, UUID supplierId, UUID itemId, UUID warehouseId) {
        MapSqlParameterSource p = params(month, supplierId, itemId);
        StringBuilder sql = new StringBuilder("""
                SELECT s.id AS sid, s.code AS scode, s.name AS sname,
                       i.id AS iid, i.code AS icode, i.name AS iname, i.image_url, l.unit,
                       sum(l.received_qty) AS qty,
                       sum(""" + AMOUNT.formatted("l.received_qty") + """
                       ) AS amount,
                       count(DISTINCT r.id) AS cnt
                """).append(FROM).append(WHERE).append(" AND l.received_qty > 0");
        filters(sql, p, supplierId, itemId);
        if (warehouseId != null) {
            sql.append(" AND r.target_warehouse_id = :wh");
            p.addValue("wh", warehouseId);
        }
        sql.append(" GROUP BY s.id, s.code, s.name, i.id, i.code, i.name, i.image_url, l.unit");

        List<Summary> rows = jdbc.query(sql.toString(), p, (rs, n) -> new Summary(
                uuid(rs, "sid"), supplier(rs), uuid(rs, "iid"), new ReferenceValue(rs.getString("icode"), rs.getString("iname")),
                rs.getString("image_url"), rs.getString("unit"), rs.getBigDecimal("qty"), rs.getBigDecimal("amount"),
                rs.getLong("cnt"), null));

        Map<UUID, Packaging> pack = defaultPackaging(rows.stream().map(Summary::itemId).collect(java.util.stream.Collectors.toSet()));
        java.text.Collator vi = java.text.Collator.getInstance(Locale.forLanguageTag("vi"));
        return rows.stream()
                .map(r -> new Summary(r.supplierId(), r.supplier(), r.itemId(), r.item(), r.imageUrl(), r.unit(),
                        r.qtyReceived(), r.amount(), r.requestCount(), pack.get(r.itemId())))
                .sorted(Comparator.comparing((Summary r) -> r.item().name(), vi)
                        .thenComparing(r -> r.supplier().name(), vi)
                        .thenComparing(Summary::unit))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Shortage> shortages(String month, UUID supplierId, UUID itemId) {
        MapSqlParameterSource p = params(month, supplierId, itemId);
        StringBuilder sql = new StringBuilder("""
                SELECT r.id AS rid, r.code, r.received_date, r.received_by,
                       s.id AS sid, s.code AS scode, s.name AS sname, w.code AS wcode, w.name AS wname,
                       i.id AS iid, i.code AS icode, i.name AS iname, i.image_url, l.unit,
                       l.quantity, l.received_qty, l.unit_cost, l.note,
                       (l.quantity - l.received_qty) AS short_qty,
                       """ + AMOUNT.formatted("(l.quantity - l.received_qty)") + """
                        AS short_amount
                """).append(FROM).append("LEFT JOIN warehouse w ON w.id = r.target_warehouse_id\n")
                .append(WHERE).append(" AND l.received_qty IS NOT NULL AND l.received_qty < l.quantity");
        filters(sql, p, supplierId, itemId);
        sql.append(" ORDER BY r.received_date DESC, r.code, l.sort_order");

        return jdbc.query(sql.toString(), p, (rs, n) -> new Shortage(
                uuid(rs, "rid"), rs.getString("code"), rs.getObject("received_date", LocalDate.class), rs.getString("received_by"),
                uuid(rs, "sid"), supplier(rs),
                rs.getString("wcode") == null ? null : new ReferenceValue(rs.getString("wcode"), rs.getString("wname")),
                uuid(rs, "iid"), new ReferenceValue(rs.getString("icode"), rs.getString("iname")),
                rs.getString("image_url"), rs.getString("unit"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("received_qty"), rs.getBigDecimal("short_qty"),
                rs.getBigDecimal("unit_cost"), rs.getBigDecimal("short_amount"), rs.getString("note")));
    }

    // ── helpers ──────────────────────────────────────────────

    private static MapSqlParameterSource params(String month, UUID supplierId, UUID itemId) {
        YearMonth ym;
        try {
            if (month == null || month.isBlank()) throw new IllegalArgumentException("month là bắt buộc (yyyy-MM)");
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("month phải có dạng yyyy-MM: " + month);
        }
        return new MapSqlParameterSource()
                .addValue("from", ym.atDay(1))
                .addValue("to", ym.plusMonths(1).atDay(1));
    }

    private static void filters(StringBuilder sql, MapSqlParameterSource p, UUID supplierId, UUID itemId) {
        if (supplierId != null) {
            sql.append(" AND r.supplier_id = :sup");
            p.addValue("sup", supplierId);
        }
        if (itemId != null) {
            sql.append(" AND l.item_id = :item");
            p.addValue("item", itemId);
        }
    }

    private Map<UUID, Packaging> defaultPackaging(Set<UUID> itemIds) {
        Map<UUID, Packaging> out = new HashMap<>();
        if (itemIds.isEmpty()) return out;
        jdbc.query("SELECT item_id, name, qty_per_pack FROM item_packaging WHERE is_default AND item_id IN (:ids)",
                new MapSqlParameterSource("ids", itemIds),
                (RowCallbackAdapter) rs -> out.put(rs.getObject("item_id", UUID.class),
                        new Packaging(rs.getString("name"), rs.getBigDecimal("qty_per_pack"))));
        return out;
    }

    private interface RowCallbackAdapter extends org.springframework.jdbc.core.RowCallbackHandler {
        void process(ResultSet rs) throws SQLException;
        @Override default void processRow(ResultSet rs) throws SQLException { process(rs); }
    }

    private static UUID uuid(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, UUID.class);
    }

    private static ReferenceValue supplier(ResultSet rs) throws SQLException {
        return rs.getObject("sid") == null ? NO_SUPPLIER : new ReferenceValue(rs.getString("scode"), rs.getString("sname"));
    }
}
