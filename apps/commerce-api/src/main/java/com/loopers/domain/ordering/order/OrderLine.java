package com.loopers.domain.ordering.order;

import com.loopers.support.domain.DomainEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class OrderLine extends DomainEntity {

    private Long productId;

    private String productName;

    private Long unitPrice;

    private Integer quantity;

    private Long lineAmount;

    public OrderLine(Long productId, String productName, Long unitPrice, Integer quantity) {
        validateProductId(productId);
        validateProductName(productName);
        validateUnitPrice(unitPrice);
        validateQuantity(quantity);

        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineAmount = unitPrice * quantity;
    }

    public static OrderLine reconstruct(
        Long id,
        Long productId,
        String productName,
        Long unitPrice,
        Integer quantity,
        Long lineAmount,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        OrderLine orderLine = new OrderLine(productId, productName, unitPrice, quantity);
        orderLine.lineAmount = lineAmount == null ? orderLine.lineAmount : lineAmount;
        orderLine.assignMetadata(id, createdAt, updatedAt, deletedAt);
        return orderLine;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Long getUnitPrice() {
        return unitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Long getLineAmount() {
        return lineAmount;
    }

    private void validateProductId(Long value) {
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }

    private void validateProductName(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품명은 비어있을 수 없습니다.");
        }
    }

    private void validateUnitPrice(Long value) {
        if (value == null || value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품 단가는 0 이상이어야 합니다.");
        }
    }

    private void validateQuantity(Integer value) {
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}
