package com.bakery.api.production.service;

import java.util.List;
import java.util.UUID;

import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.production.dto.ItemGroupRequest;
import com.bakery.api.production.dto.ItemGroupResponse;
import com.bakery.api.production.entity.ItemGroup;
import com.bakery.api.production.repository.ItemGroupRepository;
import com.bakery.api.production.repository.ProductionGroupRepository;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.framework.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemGroupService {

    private final ItemGroupRepository repository;
    private final ItemLookupRepository itemRepository;
    private final ProductionGroupRepository productionGroupRepository;

    public List<ItemGroupResponse> findAll() {
        return repository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(ItemGroupResponse::from)
                .toList();
    }

    public ItemGroupResponse findById(UUID id) {
        return ItemGroupResponse.from(getById(id));
    }

    @Transactional
    public ItemGroupResponse create(ItemGroupRequest req) {
        ItemGroup e = new ItemGroup();
        e.setCode(req.code().toUpperCase());
        e.setName(req.name());
        e.setSortOrder(req.sortOrder());
        return ItemGroupResponse.from(repository.save(e));
    }

    @Transactional
    public ItemGroupResponse update(UUID id, ItemGroupRequest req) {
        ItemGroup e = getById(id);
        e.setCode(req.code().toUpperCase());
        e.setName(req.name());
        e.setSortOrder(req.sortOrder());
        return ItemGroupResponse.from(repository.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        // 21/BE-3: không xóa nhóm còn được tham chiếu (FK item.item_group_id, production_group.item_group_id)
        String msg = deleteBlocker(
                itemRepository.countByItemGroupIdAndStatus(id, EntityStatus.ACTIVE),
                itemRepository.countByItemGroupIdAndStatus(id, EntityStatus.INACTIVE),
                productionGroupRepository.countByItemGroupId(id));
        if (msg != null) throw new IllegalStateException(msg);
        repository.deleteById(id);
    }

    /** Lý do không xóa được nhóm hàng, null nếu xóa được. active/inactive = số mặt hàng đang dùng / đã xóa mềm. */
    static String deleteBlocker(long active, long inactive, long planGroups) {
        List<String> why = new java.util.ArrayList<>();
        if (active > 0) why.add("Nhóm đang có " + active + " mặt hàng, chuyển mặt hàng sang nhóm khác trước khi xóa");
        if (inactive > 0) why.add("Nhóm còn " + inactive + " mặt hàng đã xóa vẫn thuộc nhóm này, không thể xóa nhóm");
        if (planGroups > 0) why.add("Nhóm đang được " + planGroups + " nhóm kế hoạch sản xuất sử dụng, gỡ liên kết trước khi xóa");
        return why.isEmpty() ? null : String.join("; ", why);
    }

    private ItemGroup getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ItemGroup", id));
    }
}
