package com.bakery.framework.entity;

public enum ProductionType {
    /** Sản xuất theo kế hoạch ngày */
    DAILY,

    /** Sản xuất theo đơn đặt hàng phát sinh */
    ORDER,

    /** Sản xuất bán thành phẩm (sub-production) — không giao shop, nhập thẳng vào kho bếp */
    SEMI
}
