package com.loopers.domain.inventory;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class InventoryEntity extends BaseEntity {

    private Long productId;
    private Integer quantity;

    protected InventoryEntity() {}

    public InventoryEntity(Long productId, Integer quantity) {
        validateProductId(productId);
        validateQuantity(quantity);
        this.productId = productId;
        this.quantity = quantity;
    }

    public static InventoryEntity of(Long id, Long productId, Integer quantity,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        InventoryEntity entity = new InventoryEntity();
        entity.productId = productId;
        entity.quantity = quantity;
        entity.reconstruct(id, createdAt, updatedAt, deletedAt);
        return entity;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void deduct(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (amount > this.quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.quantity -= amount;
    }

    public void updateQuantity(Integer newQuantity) {
        validateQuantity(newQuantity);
        this.quantity = newQuantity;
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다.");
        }
    }
}
