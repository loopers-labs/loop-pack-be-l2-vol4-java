package com.loopers.domain.inventory;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {
    InventoryEntity save(InventoryEntity inventory);
    Optional<InventoryEntity> findByProductId(Long productId);
    List<InventoryEntity> findAllByProductIds(List<Long> productIds);
    List<InventoryEntity> findAllByProductIdsWithLock(List<Long> productIds);
    void deleteByProductId(Long productId);
    void deleteAllByProductIds(List<Long> productIds);
}
