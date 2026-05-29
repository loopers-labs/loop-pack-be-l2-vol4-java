package com.loopers.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSnapshot {

    @Column(name = "snapshot_name", nullable = false)
    private String name;

    @Column(name = "snapshot_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal price;

    @Column(name = "snapshot_brand_name", nullable = false)
    private String brandName;

    public ProductSnapshot(String name, BigDecimal price, String brandName) {
        this.name = name;
        this.price = price;
        this.brandName = brandName;
    }
}
