package com.loopers.inventory.application;

import com.loopers.inventory.domain.Inventory;
import com.loopers.inventory.domain.InventoryRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public Inventory create(Long productId, Integer quantity) {
        return inventoryRepository.save(new Inventory(productId, quantity));
    }

    public Inventory getByProductId(Long productId) {
        return inventoryRepository
            .findByProductId(productId)
            .orElseThrow(
                () ->
                    new CoreException(
                        ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
    }

    /** 상품 ID 묶음의 재고 수량을 한 번에 조회한다(락 없음, 목록/상세 노출용). */
    public Map<Long, Integer> getQuantityMap(Collection<Long> productIds) {
        return inventoryRepository.findAllByProductIds(productIds).stream()
            .collect(Collectors.toMap(Inventory::getProductId, Inventory::getAvailableQuantity));
    }

    /**
     * 주문을 위한 재고 비관적 락 차감.
     *
     * <p>데드락을 방지하기 위해 productId 오름차순으로 정렬해 락을 획득한다. 호출자의 트랜잭션 안에서 동작하며, 변경은 dirty checking 으로
     * 반영된다.
     */
    public void deductForOrder(Map<Long, Integer> quantityByProductId) {
        List<Long> orderedIds = quantityByProductId.keySet().stream().sorted().toList();
        Map<Long, Inventory> inventories =
            inventoryRepository.findAllByProductIdsForUpdate(orderedIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));

        for (Long productId : orderedIds) {
            Inventory inventory = inventories.get(productId);
            if (inventory == null) {
                throw new CoreException(
                    ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다.");
            }
            inventory.deduct(quantityByProductId.get(productId));
        }
    }

    /** 관리자 재고 수정용. 절대값으로 재고를 설정한다. */
    public void setQuantity(Long productId, Integer quantity) {
        getByProductId(productId).changeQuantity(quantity);
    }

    public void restore(Long productId, int quantity) {
        getByProductId(productId).restore(quantity);
    }
}
