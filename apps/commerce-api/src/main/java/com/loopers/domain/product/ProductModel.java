package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductModel extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Embedded
    private Name name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Embedded
    private Price price;

    @Embedded
    private Stock stock;

    @Builder
    private ProductModel(Long brandId, String rawName, String rawDescription, Integer rawPrice, Integer rawStock) {
        this.brandId = brandId;
        this.name = Name.from(rawName);
        this.description = rawDescription;
        this.price = Price.from(rawPrice);
        this.stock = Stock.from(rawStock);
    }

    public void update(String rawName, String rawDescription, Integer rawPrice, Integer rawStock) {
        this.name = Name.from(rawName);
        this.description = rawDescription;
        this.price = Price.from(rawPrice);
        this.stock = Stock.from(rawStock);
    }
}
