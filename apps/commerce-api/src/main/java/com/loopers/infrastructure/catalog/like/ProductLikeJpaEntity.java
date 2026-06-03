package com.loopers.infrastructure.catalog.like;

import com.loopers.domain.catalog.like.ProductLike;
import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "product_like",
    indexes = {
        @Index(name = "idx_product_like_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_product_like_product", columnList = "product_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_like_user_product", columnNames = {"user_id", "product_id"})
    }
)
public class ProductLikeJpaEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    protected ProductLikeJpaEntity() {}

    private ProductLikeJpaEntity(String userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public static ProductLikeJpaEntity from(ProductLike productLike) {
        return new ProductLikeJpaEntity(productLike.getUserId(), productLike.getProductId());
    }

    public ProductLike toDomain() {
        return ProductLike.reconstruct(
            getId(),
            userId,
            productId,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }
}
