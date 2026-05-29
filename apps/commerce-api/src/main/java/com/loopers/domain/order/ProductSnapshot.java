package com.loopers.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 주문 시점의 상품 정보 스냅샷 (Value Object).
 * 상품이 수정/삭제된 이후에도 주문 당시 데이터를 보존한다.
 */
@Embeddable
public class ProductSnapshot {

    @Column(name = "snapshot_product_name", nullable = false)
    private String productName;

    @Column(name = "snapshot_price", nullable = false)
    private Long price;

    @Column(name = "snapshot_brand_name", nullable = false)
    private String brandName;

    protected ProductSnapshot() {}

    public ProductSnapshot(String productName, Long price, String brandName) {
        this.productName = productName;
        this.price = price;
        this.brandName = brandName;
    }

    public String getProductName() { return productName; }
    public Long getPrice() { return price; }
    public String getBrandName() { return brandName; }
}
