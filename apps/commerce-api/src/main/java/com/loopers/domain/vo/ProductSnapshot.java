package com.loopers.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

/** 주문 시점 상품 정보 스냅샷 — 이후 상품 수정/삭제에 영향받지 않음 */
@Embeddable
@Getter
public class ProductSnapshot {

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "price", nullable = false)
    private Long price;

    protected ProductSnapshot() {}

    public ProductSnapshot(String productName, String brandName, Long price) {
        this.productName = productName;
        this.brandName = brandName;
        this.price = price;
    }
}
