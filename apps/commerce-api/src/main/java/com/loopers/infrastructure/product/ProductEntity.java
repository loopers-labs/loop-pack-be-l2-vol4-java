package com.loopers.infrastructure.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Entity(name = "Product")
@Table(
    name = "product",
    indexes = {
        @Index(name = "idx_product_deleted_at_created_at",          columnList = "deleted_at, created_at"),
        @Index(name = "idx_product_deleted_at_price",               columnList = "deleted_at, price"),
        @Index(name = "idx_product_deleted_at_like_count",          columnList = "deleted_at, like_count"),
        @Index(name = "idx_product_brand_id_deleted_at_created_at", columnList = "brand_id, deleted_at, created_at"),
        @Index(name = "idx_product_brand_id_deleted_at_price",      columnList = "brand_id, deleted_at, price"),
        @Index(name = "idx_product_brand_id_deleted_at_like_count", columnList = "brand_id, deleted_at, like_count")
    }
)
public class ProductEntity extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private long likeCount = 0;

    protected ProductEntity() {}

    public ProductEntity(Long brandId, String name, BigDecimal price) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
    }

    public Product toDomain() {
        return new Product(getId(), brandId, name, price, likeCount,
            getCreatedAt(), getUpdatedAt(), getDeletedAt());
    }

    public void updateFrom(Product domain) {
        this.brandId = domain.getBrandId();
        this.name = domain.getName();
        this.price = domain.getPrice();
        this.likeCount = domain.getLikeCount();
        if (domain.getDeletedAt() != null) {
            delete();
        } else {
            restore();
        }
    }
}
