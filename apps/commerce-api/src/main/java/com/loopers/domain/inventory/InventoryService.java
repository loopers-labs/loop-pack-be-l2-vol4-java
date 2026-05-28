package com.loopers.domain.inventory;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional
    public InventoryEntity create(Long productId, Integer quantity) {
        return inventoryRepository.save(new InventoryEntity(productId, quantity));
    }

    @Transactional(readOnly = true)
    public InventoryEntity getByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
    }

    @Transactional
    public void updateQuantity(Long productId, Integer quantity) {
        InventoryEntity inventory = getByProductId(productId);
        inventory.updateQuantity(quantity);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void deductAll(Map<Long, Integer> productQuantities) {
        List<Long> productIds = productQuantities.keySet().stream().sorted().toList();
        List<InventoryEntity> inventories = inventoryRepository.findAllByProductIdsWithLock(productIds);
        inventories.forEach(inventory -> inventory.deduct(productQuantities.get(inventory.getProductId())));
        inventories.forEach(inventoryRepository::save);
    }

    @Transactional
    public void deleteByProduct(Long productId) {
        inventoryRepository.deleteByProductId(productId);
    }

    @Transactional
    public void deleteAllByProducts(List<Long> productIds) {
        inventoryRepository.deleteAllByProductIds(productIds);
    }
}
