package com.loopers.inventory.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository {

    Inventory save(Inventory inventory);

    Optional<Inventory> findByProductId(Long productId);

    List<Inventory> findAllByProductIds(Collection<Long> productIds);

    /** 비관적 쓰기 락(SELECT ... FOR UPDATE)으로 재고를 조회한다. 동시 주문의 초과 판매를 방지한다. */
    List<Inventory> findAllByProductIdsForUpdate(Collection<Long> productIds);
}
