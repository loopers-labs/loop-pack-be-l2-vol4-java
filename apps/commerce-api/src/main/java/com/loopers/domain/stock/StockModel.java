package com.loopers.domain.stock;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "stocks")
public class StockModel extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true, updatable = false)
    private ProductModel product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected StockModel() {}

    public StockModel(ProductModel product, int quantity) {
        if (product == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품은 필수입니다.");
        }
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.product = product;
        this.quantity = quantity;
    }

    /**
     * 재고를 차감한다. 차감 수량이 0 이하이거나 현재 재고보다 많으면 예외를 던진다.
     */
    public void decrease(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 0보다 커야 합니다.");
        }
        if (this.quantity < amount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.quantity -= amount;
    }
}
