package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLike;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "product_like")
public class ProductLikeEntity {

    @EmbeddedId
    private ProductLikeId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected ProductLikeEntity() {}

    public ProductLikeEntity(Long userId, Long productId) {
        this.id = new ProductLikeId(userId, productId);
        this.createdAt = ZonedDateTime.now();
    }

    public ProductLike toDomain() {
        return new ProductLike(id.getUserId(), id.getProductId(), createdAt);
    }

    public static ProductLikeEntity from(ProductLike domain) {
        ProductLikeEntity entity = new ProductLikeEntity(domain.getUserId(), domain.getProductId());
        return entity;
    }
}
