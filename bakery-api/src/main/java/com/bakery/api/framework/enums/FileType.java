package com.bakery.api.framework.enums;

public enum FileType {
    /** BanhRaNgay.xlsx — Admin tạo, bếp cần làm bao nhiêu */
    PRODUCTION_REQUEST,
    /** XuatRa.xlsx — Bếp xuất thực tế */
    PRODUCTION_ACTUAL,
    /** BaoCaoNgay.xlsx — Nhân viên tổng hợp kiểm kê */
    DAILY_INVENTORY,
    /** BigProductBySaleByCat — Export từ máy POS */
    POS_EXPORT,
    /** File công thức sản phẩm */
    RECIPE,
    /** File bán thành phẩm (phôi/nhân) */
    SEMI_PRODUCT,
    /** File danh mục sản phẩm */
    PRODUCT,
    /** File giá nguyên liệu */
    INGREDIENT_PRICE
}
