package com.loopers.infrastructure.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Product;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class ProductJpaEntity extends BaseEntity {

    private Long brandId;
    private String name;
    private String description;
    private Long price;
    private Integer stock;
    private Integer likeCount;

    protected ProductJpaEntity() {
    }

    private ProductJpaEntity(Long brandId, String name, String description, Long price, Integer stock, Integer likeCount) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.likeCount = likeCount;
    }

    public static ProductJpaEntity from(Product product) {
        return new ProductJpaEntity(
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getLikeCount()
        );
    }

    public Product toDomain() {
        return Product.reconstruct(
            getId(),
            brandId,
            name,
            description,
            price,
            stock,
            likeCount,
            getDeletedAt() != null
        );
    }

    public void update(Product product) {
        this.brandId = product.getBrandId();
        this.name = product.getName();
        this.description = product.getDescription();
        this.price = product.getPrice();
        this.stock = product.getStock();
        this.likeCount = product.getLikeCount();
        if (product.isDeleted()) {
            delete();
        }
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Long getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public Integer getLikeCount() {
        return likeCount;
    }
}
