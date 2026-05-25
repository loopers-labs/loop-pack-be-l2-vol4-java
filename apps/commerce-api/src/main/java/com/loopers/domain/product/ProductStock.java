package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "product_stock")
@Getter
public class ProductStock {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    protected ProductStock() {}

    public ProductStock(Long productId, long quantity) {
        validate(productId, quantity);
        this.productId = productId;
        this.quantity = quantity;
        this.updatedAt = ZonedDateTime.now();
    }

    public void deduct(long amount) {
        if (this.quantity - amount < 0) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다.");
        }
        this.quantity -= amount;
        this.updatedAt = ZonedDateTime.now();
    }

    public void updateQuantity(long quantity) {
        validate(this.productId, quantity);
        this.quantity = quantity;
        this.updatedAt = ZonedDateTime.now();
    }

    private void validate(Long productId, long quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }
}
