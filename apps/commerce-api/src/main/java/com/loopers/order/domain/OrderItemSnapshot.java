package com.loopers.order.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.util.Objects;

/** 주문 당시 상품 정보를 고정 보존하는 값 객체(Value Object). */
@Getter
@Embeddable
public class OrderItemSnapshot {

    @Column(name = "snapshot_product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "snapshot_product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "snapshot_brand_name", updatable = false)
    private String brandName;

    @Column(name = "snapshot_unit_price", nullable = false, updatable = false)
    private Long unitPrice;

    protected OrderItemSnapshot() {}

    public OrderItemSnapshot(Long productId, String productName, String brandName, Long unitPrice) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "스냅샷 상품 식별자는 필수입니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "스냅샷 상품명은 필수입니다.");
        }
        if (unitPrice == null || unitPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "스냅샷 단가는 0 이상이어야 합니다.");
        }
        this.productId = productId;
        this.productName = productName;
        this.brandName = brandName;
        this.unitPrice = unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OrderItemSnapshot that)) {
            return false;
        }
        return Objects.equals(productId, that.productId)
            && Objects.equals(productName, that.productName)
            && Objects.equals(brandName, that.brandName)
            && Objects.equals(unitPrice, that.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, productName, brandName, unitPrice);
    }
}
