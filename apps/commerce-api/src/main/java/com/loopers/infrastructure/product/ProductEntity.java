package com.loopers.infrastructure.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product")
public class ProductEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private BrandEntity brand;

    private String name;
    private String description;
    private Long price;
    private Integer stock;

    private ProductEntity(BrandEntity brand, String name, String description, Long price, Integer stock) {
        this.brand = brand;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    public static ProductEntity from(ProductModel model, BrandEntity brand) {
        return new ProductEntity(brand, model.getName(), model.getDescription(), model.getPrice(), model.getStock());
    }

    public void update(String name, String description, Long price, Integer stock) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    public ProductModel toDomain() {
        return new ProductModel(
            getId(),
            brand.getId(),
            name,
            description,
            price,
            stock,
            getCreatedAt(),
            getUpdatedAt()
        );
    }
}
