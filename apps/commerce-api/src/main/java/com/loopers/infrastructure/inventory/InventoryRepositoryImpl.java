package com.loopers.infrastructure.inventory;

import com.loopers.domain.inventory.InventoryEntity;
import com.loopers.domain.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class InventoryRepositoryImpl implements InventoryRepository {

    private final InventoryJpaRepository inventoryJpaRepository;

    @Override
    public InventoryEntity save(InventoryEntity inventory) {
        return InventoryMapper.toDomain(inventoryJpaRepository.save(InventoryMapper.toJpaEntity(inventory)));
    }

    @Override
    public Optional<InventoryEntity> findByProductId(Long productId) {
        return inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productId)
                .map(InventoryMapper::toDomain);
    }

    @Override
    public List<InventoryEntity> findAllByProductIdsWithLock(List<Long> productIds) {
        return inventoryJpaRepository.findAllByProductIdsWithLock(productIds).stream()
                .map(InventoryMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteByProductId(Long productId) {
        inventoryJpaRepository.softDeleteByProductId(productId, ZonedDateTime.now());
    }

    @Override
    public void deleteAllByProductIds(List<Long> productIds) {
        inventoryJpaRepository.softDeleteAllByProductIds(productIds, ZonedDateTime.now());
    }
}
