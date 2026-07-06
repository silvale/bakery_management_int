package com.bakery.api.framework.dto;

import lombok.Getter;

import java.util.List;

/**
 * Wrapper cho paginated response.
 * Dùng nhất quán trong mọi list API của admin.
 */
@Getter
public class PageResult<T> {

    private final List<T> data;
    private final int page;
    private final int size;
    private final long total;
    private final int totalPages;

    public PageResult(List<T> data, int page, int size, long total) {
        this.data       = data;
        this.page       = page;
        this.size       = size;
        this.total      = total;
        this.totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
    }

    public static <T> PageResult<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResult<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements()
        );
    }
}
