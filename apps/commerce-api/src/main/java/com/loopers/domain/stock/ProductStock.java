package com.loopers.domain.stock;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_stock")
public class ProductStock extends BaseEntity {

    private Long productId;
    private int quantity;

    private ProductStock(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public static ProductStock create(Long productId, int quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        return new ProductStock(productId, quantity);
    }

    public boolean hasStock(int quantity) {
        return this.quantity >= quantity;
    }

    public void deduct(int quantity) {
        if (!hasStock(quantity)) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다.");
        }
        this.quantity -= quantity;
    }
}
