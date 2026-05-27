package com.loopers.order.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItemModel extends BaseEntity {

    private Long productId;
    private String productName;
    private Long price;
    private Integer quantity;

    protected OrderItemModel() {}

    public OrderItemModel(Long productId, String productName, Long price, Integer quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId는 비어있을 수 없습니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 비어있을 수 없습니다.");
        }
        if (price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
        if (quantity == null || quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }

        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Long getPrice() { return price; }
    public Integer getQuantity() { return quantity; }
}
