package com.bakery.api.framework.dtos;

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

    /** Wrap một List đã lấy từ DB (aggregate queries không dùng Spring Page). */
    public static <T> PageResult<T> ofList(List<T> paged, long totalCount, int page, int size) {
        return new PageResult<>(paged, page, size, totalCount);
    }

    /** Wrap toàn bộ list không phân trang (total = list.size()). */
    public static <T> PageResult<T> ofAll(List<T> all) {
        return new PageResult<>(all, 0, all.size(), all.size());
    }
}
