package com.bakery.api.framework.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ReconcileResultResponse {

    private LocalDate reconDate;
    private String    batchRunId;
    private String    batchStatus;
    private boolean   isRerun;

    /** Tóm tắt tổng thể */
    private Summary summary;

    /** Chi tiết từng sản phẩm */
    private List<ProductReconcile> products;

    /** Log từng file import */
    private List<FileImportResult> fileImports;

    @Data
    @Builder
    public static class Summary {
        private int         totalProducts;
        private int         okCount;
        private int         discrepancyCount;
        private int         pendingCount;
        private BigDecimal  totalRevenue;
        private BigDecimal  totalSalesCost;
        private BigDecimal  totalCancelledCost;
        private BigDecimal  totalGrossProfit;
    }

    @Data
    @Builder
    public static class ProductReconcile {
        private String     productCode;
        private String     productName;
        private String     unit;

        // Tầng 1: Sản xuất
        private BigDecimal qtyRequested;
        private BigDecimal qtyProduced;
        private BigDecimal productionDiff;
        private String     productionStatus;  // OK | OVER | UNDER | PENDING

        // Tầng 2: Vận chuyển
        private BigDecimal qtySent;
        private BigDecimal qtyReceived;
        private BigDecimal deliveryDiff;
        private String     deliveryStatus;    // OK | DISCREPANCY | PENDING

        // Tầng 3: Bán hàng
        private BigDecimal qtySoldPos;
        private BigDecimal qtySoldDerived;   // từ kiểm kê
        private BigDecimal qtyOpening;
        private BigDecimal qtyCancelled;
        private BigDecimal qtyClosing;
        private BigDecimal posDiff;
        private String     posStatus;         // OK | DISCREPANCY | PENDING

        // Cost & Profit
        private BigDecimal costPerUnit;
        private BigDecimal unitPrice;
        private BigDecimal revenue;
        private BigDecimal salesCost;
        private BigDecimal cancelledCost;
        private BigDecimal grossProfit;

        // Tổng
        private String     overallStatus;
        private String     discrepancyNote;
    }

    @Data
    @Builder
    public static class FileImportResult {
        private String  fileName;
        private String  fileType;
        private String  status;
        private Integer rowsTotal;
        private Integer rowsOk;
        private Integer rowsError;
        private String  errorDetail;
        private Object  errorRowIndices;
    }
}
