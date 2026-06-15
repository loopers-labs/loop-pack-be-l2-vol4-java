package com.loopers.infrastructure.inventory;

import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class InventoryRepositoryImpl implements InventoryRepository {

    private final InventoryJpaRepository inventoryJpaRepository;

    @Override
    public Inventory save(Inventory inventory) {
        return inventoryJpaRepository.save(inventory);
    }

    @Override
    public void update(Inventory inventory) {
        inventoryJpaRepository.save(inventory);
    }

    @Override
    public Optional<Inventory> find(Long productId) {
        return inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productId);
    }

    @Override
    public List<Inventory> findAllByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return inventoryJpaRepository.findAllByProductIdInAndDeletedAtIsNull(productIds);
    }

    @Override
    public List<Inventory> findAllByProductIdsForUpdate(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return inventoryJpaRepository.findAllByProductIdInAndDeletedAtIsNullOrderByProductIdAsc(productIds);
    }

    @Override
    public int bulkSoftDeleteByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return 0;
        }
        return inventoryJpaRepository.bulkSoftDeleteByProductIds(productIds);
    }
}
