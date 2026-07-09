package com.bakery.api.master.repository;

import java.util.List;

import com.bakery.api.master.entity.CodeValue;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.framework.repository.BaseRepository;

public interface CodeValueRepository extends BaseRepository<CodeValue> {
    List<CodeValue> findByGroupKeyAndStatusOrderBySortOrderAsc(String groupKey, EntityStatus status);
}
