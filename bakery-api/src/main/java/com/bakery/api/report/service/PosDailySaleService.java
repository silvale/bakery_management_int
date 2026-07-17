package com.bakery.api.report.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


import com.bakery.api.master.repository.ProductMappingRepository;
import com.bakery.api.report.entity.PosDailySale;
import com.bakery.api.report.repository.PosDailySaleRepository;
import com.bakery.framework.security.BakeryActorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Parse file Excel từ máy POS và lưu vào pos_daily_sale.
 *
 * <p>Format file POS (mỗi hàng 1 SKU):
 * Cột A: EX_CODE (mã từ POS)
 * Cột B: Tên sản phẩm
 * Cột C: Số lượng bán (qty_sold)
 * Cột D: Tổng tiền (total_amount)
 *
 * <p>unit_price = total_amount / qty_sold (làm tròn 2 chữ số)
 * Map EX_CODE → item qua bảng product_mapping.
 * Upload lại cùng ngày sẽ xóa data cũ và ghi đè.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosDailySaleService {

    private final PosDailySaleRepository repository;
    private final ProductMappingRepository productMappingRepository;
    private final BakeryActorResolver actorResolver;

    public List<PosDailySale> findBySaleDate(LocalDate saleDate) {
        return repository.findBySaleDate(saleDate);
    }

    /**
     * Như findBySaleDate nhưng resolve exCode → itemId qua product_mapping.
     * Trả về List<Map> với field "itemId" (UUID) nếu map được.
     */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> findBySaleDateMapped(LocalDate saleDate) {
        return repository.findBySaleDate(saleDate).stream().map(ps -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("exCode", ps.getExCode());
            m.put("itemName", ps.getItemName() != null ? ps.getItemName() : "");
            m.put("qtySold", ps.getQtySold());
            m.put("unitPrice", ps.getUnitPrice());
            m.put("totalAmount", ps.getTotalAmount());
            productMappingRepository.findItemIdByExCode(ps.getExCode())
                    .ifPresent(itemId -> m.put("itemId", itemId));
            return m;
        }).toList();
    }

    /**
     * Upload file POS cho ngày cụ thể.
     * Nếu đã có data của ngày đó → xóa và ghi đè.
     *
     * @return danh sách records đã lưu (kèm warning nếu EX_CODE không map được)
     */
    @Transactional
    public UploadResult upload(LocalDate saleDate, MultipartFile file) {
        List<PosDailySale> saved = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Xóa data cũ của ngày này nếu có
        repository.deleteBySaleDate(saleDate);

        try (InputStream is = file.getInputStream();
             Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(is)) {

            Sheet sheet = wb.getSheetAt(0);
            String actor = actorResolver.currentUserId();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // skip header
                if (isRowEmpty(row)) continue;

                String exCode = getCellString(row, 1);
                String itemName = getCellString(row, 2);
                BigDecimal qtySold = getCellDecimal(row, 5);
                BigDecimal totalAmount = getCellDecimal(row, 6);

                if (exCode == null || exCode.isBlank() || qtySold == null
                        || qtySold.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // Kiểm tra map được không (log warning nhưng vẫn lưu)
                if (!productMappingRepository.existsByExCode(exCode)) {
                    warnings.add("EX_CODE không có trong product_mapping: " + exCode);
                    log.warn("POS upload: EX_CODE '{}' không map được → bỏ qua", exCode);
                    continue;
                }

                BigDecimal unitPrice = null;
                if (totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                    unitPrice = totalAmount.divide(qtySold, 2, RoundingMode.HALF_UP);
                }

                PosDailySale sale = new PosDailySale();
                sale.setSaleDate(saleDate);
                sale.setExCode(exCode);
                sale.setItemName(itemName);
                sale.setQtySold(qtySold);
                sale.setUnitPrice(unitPrice);
                sale.setTotalAmount(totalAmount);
                sale.setCreatedBy(actor);
                saved.add(repository.save(sale));
            }

        } catch (Exception e) {
            throw new RuntimeException("Lỗi parse file POS: " + e.getMessage(), e);
        }

        return new UploadResult(saved.size(), warnings);
    }

    // ── Excel helpers ────────────────────────────────────────────

    private boolean isRowEmpty(Row row) {
        for (int c = 0; c <= 3; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private BigDecimal getCellDecimal(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> {
                try { yield new BigDecimal(cell.getStringCellValue().trim()); }
                catch (NumberFormatException e) { yield null; }
            }
            default -> null;
        };
    }

    public record UploadResult(int savedCount, List<String> warnings) {}
}
