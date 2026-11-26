package com.bakery.framework.exception;

import java.util.Map;

/** 26: tên tiếng Việt của thực thể (tên class) để ghép vào câu lỗi trả cho người dùng. */
public final class EntityNames {
    private EntityNames() {}

    private static final Map<String, String> VI = Map.ofEntries(
            Map.entry("Item", "mặt hàng"), Map.entry("Product", "sản phẩm"), Map.entry("SemiProduct", "hàng gia công"),
            Map.entry("Ingredient", "nguyên liệu"), Map.entry("Warehouse", "kho"), Map.entry("Store", "cửa hàng"),
            Map.entry("Supplier", "nhà cung cấp"), Map.entry("ItemGroup", "nhóm hàng hóa"),
            Map.entry("ProductionGroup", "nhóm sản xuất"), Map.entry("ProductionPlan", "kế hoạch sản xuất"),
            Map.entry("ProductionRequest", "phiếu sản xuất"), Map.entry("ProductionRequestLine", "dòng phiếu sản xuất"),
            Map.entry("ProductionAdjustment", "phiếu điều chỉnh sản lượng"), Map.entry("ProductionSession", "phiên sản xuất"),
            Map.entry("DeliveryRecord", "bản ghi giao nhận"), Map.entry("CancelRecord", "phiếu hủy"),
            Map.entry("DailyReport", "báo cáo ngày"), Map.entry("CakeDesign", "mẫu bánh"),
            Map.entry("InventoryRequest", "phiếu kho"), Map.entry("InventoryRequestLine", "dòng phiếu kho"),
            Map.entry("Recipe", "công thức"), Map.entry("UserAccount", "tài khoản"), Map.entry("UserRole", "vai trò"),
            Map.entry("CodeValue", "giá trị danh mục"), Map.entry("Unit", "đơn vị"),
            Map.entry("ProductExpiryConfig", "cấu hình hạn dùng"), Map.entry("ProductMapping", "liên kết mã POS"),
            Map.entry("StockLot", "lô hàng"), Map.entry("ThresholdRule", "quy tắc sản xuất"));

    /** Tên tiếng Việt; thực thể chưa có trong bảng → "dữ liệu". */
    public static String vi(String entity) { return VI.getOrDefault(entity, "dữ liệu"); }
}
