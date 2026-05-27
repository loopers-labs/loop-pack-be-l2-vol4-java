package com.loopers.domain.stock;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock")
public class StockModel extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    protected StockModel() {}

    public StockModel(Long productId, Integer quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 비어있을 수 없습니다.");
        }
        if (quantity == null || quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.productId = productId;
        this.quantity = quantity;
    }

    public void decrease(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.quantity < amount) {
            throw new CoreException(ErrorType.OUT_OF_STOCK, "재고가 부족합니다.");
        }
        this.quantity -= amount;
    }

    public void increase(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "증가 수량은 1 이상이어야 합니다.");
        }
        this.quantity += amount;
    }

    public void changeTo(int target) {
        if (target < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        int diff = target - this.quantity;
        if (diff > 0) {
            increase(diff);
        } else if (diff < 0) {
            decrease(-diff);
        }
    }

    public boolean isAvailable() {
        return this.quantity > 0;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
