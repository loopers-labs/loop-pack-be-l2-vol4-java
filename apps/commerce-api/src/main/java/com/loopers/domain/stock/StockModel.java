package com.loopers.domain.stock;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "stocks")
@SQLRestriction("deleted_at IS NULL")
public class StockModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected StockModel() {}

    public StockModel(Long productId, int quantity) {
        validateProductId(productId);
        validateQuantity(quantity);
        this.productId = productId;
        this.quantity = quantity;
    }

    public void decrease(int qty) {
        if (this.quantity < qty) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.quantity -= qty;
    }

    public void increase(int qty) {
        this.quantity += qty;
    }

    public boolean isAvailable(int qty) {
        return this.quantity >= qty;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다.");
        }
    }
}
