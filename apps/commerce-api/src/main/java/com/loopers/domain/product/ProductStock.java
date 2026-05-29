package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_stocks")
public class ProductStock extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private int stock;

    protected ProductStock() {}

    public ProductStock(Long productId, int initialStock) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
        if (initialStock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.productId = productId;
        this.stock = initialStock;
    }

    public Long getProductId() {
        return productId;
    }

    public int getStock() {
        return stock;
    }

    /**
     * 재고를 차감한다.
     * 재고 부족 시 예외를 던지는 불변식 보호가 도메인 레벨에서 처리된다.
     */
    public void decrease(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.stock < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "재고가 부족합니다. (현재: " + this.stock + ", 요청: " + quantity + ")");
        }
        this.stock -= quantity;
    }

    /**
     * 재고를 복원한다. (주문 취소 시 사용)
     */
    public void restore(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복원 수량은 1 이상이어야 합니다.");
        }
        this.stock += quantity;
    }
}
