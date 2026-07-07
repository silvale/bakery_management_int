package com.bakery.api.batch.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thu thập lỗi từng dòng trong quá trình đọc file Excel.
 * Kết quả được lưu vào FileImportLog.errorRowIndices (JSONB).
 *
 * Format output:
 *   [{"row": "5", "col": "qty_actual", "msg": "Không phải số"},
 *    {"row": "12", "col": "product_code", "msg": "Không tìm thấy trong DB"}]
 */
public class ErrorRowCollector {

    private final List<Map<String, String>> errors = new ArrayList<>();
    private int totalRows   = 0;
    private int successRows = 0;

    public void addError(int rowIndex, String column, String message) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("row", String.valueOf(rowIndex + 1)); // Excel row là 1-based
        error.put("col", column);
        error.put("msg", message);
        errors.add(error);
    }

    public void incrementTotal() { totalRows++; }

    public void incrementSuccess() { successRows++; }

    public List<Map<String, String>> getErrors() {
        return List.copyOf(errors);
    }

    public int getTotalRows()   { return totalRows; }
    public int getSuccessRows() { return successRows; }
    public int getErrorRows()   { return errors.size(); }

    public boolean hasErrors()  { return !errors.isEmpty(); }

    public String buildErrorSummary() {
        if (!hasErrors()) return null;
        return String.format("Tổng: %d dòng | Thành công: %d | Lỗi: %d",
            totalRows, successRows, errors.size());
    }
}
