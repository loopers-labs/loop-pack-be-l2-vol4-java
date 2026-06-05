package com.loopers.product.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "product_stocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_stocks_product_id", columnNames = "product_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductStock extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    private ProductStock(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
        validate();
    }

    public static ProductStock create(Long productId, int quantity) {
        return new ProductStock(productId, quantity);
    }

    public void decrease(int qty) {
        if (qty <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (quantity < qty) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다.");
        }
        this.quantity -= qty;
    }

    public void increase(int qty) {
        if (qty <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "증가 수량은 1 이상이어야 합니다.");
        }
        this.quantity += qty;
    }

    private void validate() {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId 는 비어있을 수 없습니다.");
        }
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }
}
