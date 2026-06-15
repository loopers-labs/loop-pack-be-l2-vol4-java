package com.loopers.domain.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository {

    Inventory save(Inventory inventory);

    void update(Inventory inventory);

    Optional<Inventory> find(Long productId);

    // 비-락 조회 (조회 응답 조립)
    List<Inventory> findAllByProductIds(Collection<Long> productIds);

    // 비관락(FOR UPDATE) 조회 (주문 재고 차감)
    List<Inventory> findAllByProductIdsForUpdate(Collection<Long> productIds);

    // 상품 일괄 삭제의 재고 cascade (소프트 삭제)
    int bulkSoftDeleteByProductIds(Collection<Long> productIds);
}
