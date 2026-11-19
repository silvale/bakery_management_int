package com.bakery.api.production.dto;

import java.util.List;

import com.bakery.api.cake.dto.DesignQtyRequest;

/** Body tùy chọn của {@code POST /production-requests/{id}/lines/{lineId}/complete}; field cũ vẫn là query param. */
public record CompleteLineBody(List<DesignQtyRequest> designs) {}
