package com.bakery.api.production.repository;

import java.util.List;
import java.util.Optional;

import com.bakery.api.production.entity.ProductPlanTemplate;
import com.bakery.framework.entity.DayType;
import com.bakery.framework.repository.BaseRepository;

public interface ProductPlanTemplateRepository extends BaseRepository<ProductPlanTemplate> {
    Optional<ProductPlanTemplate> findByDayTypeAndActiveTrue(DayType dayType);
    List<ProductPlanTemplate> findByDayType(DayType dayType);
}
