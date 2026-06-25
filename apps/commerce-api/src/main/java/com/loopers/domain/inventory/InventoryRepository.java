package com.loopers.domain.inventory;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {
    InventoryEntity save(InventoryEntity inventory);
    Optional<InventoryEntity> findByProductId(String productId);
    List<InventoryEntity> findAllByProductIds(List<String> productIds);
    List<InventoryEntity> findAllByProductIdsWithLock(List<String> productIds);
    void deleteByProductId(String productId);
    void deleteAllByProductIds(List<String> productIds);
}
