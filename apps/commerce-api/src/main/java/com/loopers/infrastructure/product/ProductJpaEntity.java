package com.loopers.infrastructure.product;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "products")
public class ProductJpaEntity extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "stock_quantity", nullable = false)
    private int stock;

    private ProductJpaEntity(Long brandId, String name, long price, int stock) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public static ProductJpaEntity of(Long brandId, String name, long price, int stock) {
        return new ProductJpaEntity(brandId, name, price, stock);
    }

    public void update(String name, long price) {
        this.name = name;
        this.price = price;
    }

    public void updateStock(int stock) {
        this.stock = stock;
    }
}
