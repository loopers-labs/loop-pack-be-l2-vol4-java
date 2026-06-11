package com.loopers.infrastructure.like;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.like.ProductLike;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "product_like",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_login_id", "product_id"})
)
public class ProductLikeJpaEntity extends BaseEntity {

    @Column(name = "user_login_id", nullable = false)
    private String userLoginId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    protected ProductLikeJpaEntity() {
    }

    private ProductLikeJpaEntity(String userLoginId, Long productId) {
        this.userLoginId = userLoginId;
        this.productId = productId;
    }

    public static ProductLikeJpaEntity from(ProductLike productLike) {
        return new ProductLikeJpaEntity(productLike.getUserLoginId(), productLike.getProductId());
    }

    public ProductLike toDomain() {
        return ProductLike.reconstruct(getId(), userLoginId, productId);
    }

    public String getUserLoginId() {
        return userLoginId;
    }

    public Long getProductId() {
        return productId;
    }
}
