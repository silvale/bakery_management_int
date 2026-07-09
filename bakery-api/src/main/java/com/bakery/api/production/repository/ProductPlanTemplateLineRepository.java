package com.bakery.api.production.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductPlanTemplateLine;
import com.bakery.framework.repository.BaseRepository;

public interface ProductPlanTemplateLineRepository extends BaseRepository<ProductPlanTemplateLine> {
    List<ProductPlanTemplateLine> findByTemplateIdOrderBySortOrderAsc(UUID templateId);
}
