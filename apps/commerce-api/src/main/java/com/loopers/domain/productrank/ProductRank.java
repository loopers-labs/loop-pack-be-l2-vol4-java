package com.loopers.domain.productrank;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 읽기 전용 정렬 모델(b). likes_desc 정렬 순서(ordered product_id)만 제공한다.
 * 렌더 필드는 detail 경로에서 채운다. 스케줄러가 source(product+product_like_count)에서 적재한다.
 */
@Entity
@Table(
    name = "product_rank",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_rank_product", columnNames = "product_id"),
    indexes = {
        @Index(name = "idx_rank_brand_like", columnList = "brand_id, like_count, product_id"),
        @Index(name = "idx_rank_like", columnList = "like_count, product_id")
    }
)
public class ProductRank extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    protected ProductRank() {}

    public ProductRank(Long productId, Long brandId, long likeCount) {
        this.productId = productId;
        this.brandId = brandId;
        this.likeCount = likeCount;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public long getLikeCount() {
        return likeCount;
    }
}
