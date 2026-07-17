package com.loopers.inventory.infrastructure;

import com.loopers.inventory.domain.Inventory;
import com.loopers.inventory.domain.InventoryRepository;
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
    public Optional<Inventory> findByProductId(Long productId) {
        return inventoryJpaRepository.findByProductId(productId);
    }

    @Override
    public List<Inventory> findAllByProductIds(Collection<Long> productIds) {
        return inventoryJpaRepository.findByProductIdIn(productIds);
    }

    @Override
    public List<Inventory> findAllByProductIdsForUpdate(Collection<Long> productIds) {
        return inventoryJpaRepository.findAllByProductIdsForUpdate(productIds);
    }
}
