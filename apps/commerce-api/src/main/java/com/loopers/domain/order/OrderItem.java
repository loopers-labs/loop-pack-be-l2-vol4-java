package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_price", nullable = false)
    private Long productPrice;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    protected OrderItem() {}

    private OrderItem(Long orderId, Long productId, int quantity, String productName, Long productPrice, String brandName) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID 는 비어있을 수 없습니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 비어있을 수 없습니다.");
        }
        if (quantity < 1) {
            throw new CoreException(ErrorType.INVALID_QUANTITY, "주문 수량은 1 이상이어야 합니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명 스냅샷은 비어있을 수 없습니다.");
        }
        if (productPrice == null || productPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격 스냅샷은 0 이상이어야 합니다.");
        }
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명 스냅샷은 비어있을 수 없습니다.");
        }
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.productName = productName;
        this.productPrice = productPrice;
        this.brandName = brandName;
    }

    public static OrderItem of(Long orderId, Long productId, int quantity, String productName, Long productPrice, String brandName) {
        return new OrderItem(orderId, productId, quantity, productName, productPrice, brandName);
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getProductName() {
        return productName;
    }

    public Long getProductPrice() {
        return productPrice;
    }

    public String getBrandName() {
        return brandName;
    }
}
