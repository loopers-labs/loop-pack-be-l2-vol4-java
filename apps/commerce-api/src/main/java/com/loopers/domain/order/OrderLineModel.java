package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_line")
public class OrderLineModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Integer quantity;

    protected OrderLineModel() {}

    public OrderLineModel(Long productId, String productName, Long price, Integer quantity) {
        validateProductId(productId);
        validateProductName(productName);
        validatePrice(price);
        validateQuantity(quantity);

        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Long getPrice() {
        return price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Long getAmount() {
        return price * quantity;
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    private void validatePrice(Long price) {
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 0 이상이어야 합니다.");
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}
