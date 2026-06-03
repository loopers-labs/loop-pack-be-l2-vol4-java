package com.loopers.infrastructure.catalog.product;

import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductStatus;
import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class ProductJpaEntity extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Long price;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    protected ProductJpaEntity() {}

    private ProductJpaEntity(
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stockQuantity,
        Long likeCount,
        ProductStatus status
    ) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.likeCount = likeCount;
        this.status = status;
    }

    public static ProductJpaEntity from(Product product) {
        return new ProductJpaEntity(
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPriceAmount(),
            product.getStockQuantity(),
            product.getLikeCount(),
            product.getStatus()
        );
    }

    public Product toDomain() {
        return Product.reconstruct(
            getId(),
            brandId,
            name,
            description,
            price,
            stockQuantity,
            likeCount,
            status,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(Product product) {
        this.brandId = product.getBrandId();
        this.name = product.getName();
        this.description = product.getDescription();
        this.price = product.getPriceAmount();
        this.stockQuantity = product.getStockQuantity();
        this.likeCount = product.getLikeCount();
        this.status = product.getStatus();
        if (product.getDeletedAt() != null) {
            delete();
        } else {
            restore();
        }
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }
}
