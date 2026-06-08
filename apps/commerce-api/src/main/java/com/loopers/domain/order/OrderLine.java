package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;

public class OrderLine {

    private Long id;
    private Long productId;
    private String productName;
    private Long productPrice;
    private Integer quantity;
    private Long totalPrice;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    protected OrderLine() {}

    public OrderLine(Long id, Long productId, String productName, Long productPrice, Integer quantity, Long totalPrice, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static OrderLine create(Long productId, String productName, Long productPrice, Integer quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 null일 수 없습니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명 스냅샷은 비어있을 수 없습니다.");
        }
        if (productPrice == null || productPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 0 이상이어야 합니다.");
        }
        if (quantity == null || quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
        long totalPrice = productPrice * quantity;
        return new OrderLine(null, productId, productName, productPrice, quantity, totalPrice, null, null);
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Long getProductPrice() { return productPrice; }
    public Integer getQuantity() { return quantity; }
    public Long getTotalPrice() { return totalPrice; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
