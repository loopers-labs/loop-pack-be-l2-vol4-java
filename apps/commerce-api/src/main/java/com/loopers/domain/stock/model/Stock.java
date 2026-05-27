package com.loopers.domain.stock.model;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "stocks")
@Getter
public class Stock extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected Stock() {}

    private Stock(Long productId, int quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다.");
        }
        this.productId = productId;
        this.quantity = quantity;
    }

    public static Stock create(Long productId, int quantity) {
        return new Stock(productId, quantity);
    }

    public boolean isAvailable(int requestedQuantity) {
        return this.quantity >= requestedQuantity;
    }
}
