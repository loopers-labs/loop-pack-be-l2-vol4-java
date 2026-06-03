package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class OrderItemEntity extends BaseEntity {

    private Long orderId;
    private Long productId;
    private String productName;
    private Long productPrice;
    private Integer quantity;

    protected OrderItemEntity() {}

    public OrderItemEntity(Long productId, String productName, Long productPrice, Integer quantity) {
        validateProductId(productId);
        validateProductName(productName);
        validateProductPrice(productPrice);
        validateQuantity(quantity);
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
    }

    public static OrderItemEntity of(Long id, Long orderId, Long productId, String productName,
            Long productPrice, Integer quantity,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        OrderItemEntity entity = new OrderItemEntity();
        entity.orderId = orderId;
        entity.productId = productId;
        entity.productName = productName;
        entity.productPrice = productPrice;
        entity.quantity = quantity;
        entity.reconstruct(id, createdAt, updatedAt, deletedAt);
        return entity;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Long getProductPrice() {
        return productPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Long subtotal() {
        return productPrice * quantity;
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }

    private void validateProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    private void validateProductPrice(Long productPrice) {
        if (productPrice == null || productPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 0 이상이어야 합니다.");
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}
