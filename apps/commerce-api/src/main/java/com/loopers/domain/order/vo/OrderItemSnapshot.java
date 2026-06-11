package com.loopers.domain.order.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record OrderItemSnapshot(
    @Column(name = "brand_id", nullable = false)
    Long brandId,
    @Column(name = "brand_name", nullable = false)
    String brandName,
    @Column(name = "product_id", nullable = false)
    Long productId,
    @Column(name = "product_name", nullable = false)
    String productName
) {

    public OrderItemSnapshot {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 비어있을 수 없습니다.");
        }
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    public static OrderItemSnapshot of(Long brandId, String brandName, Long productId, String productName) {
        return new OrderItemSnapshot(brandId, brandName, productId, productName);
    }
}
