package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 상품별 좋아요 집계. like 도메인이 소유한다 (product 테이블과 쓰기 경합 분리).
 * 증감은 원자적 UPDATE 쿼리(LikeCountRepository)로만 수행한다.
 */
@Entity
@Table(
    name = "product_like_count",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_like_count_product", columnNames = {"product_id"})
)
public class ProductLikeCount extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long count;

    protected ProductLikeCount() {}

    public ProductLikeCount(Long productId, long count) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
        if (count < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 이상이어야 합니다.");
        }
        this.productId = productId;
        this.count = count;
    }

    public Long getProductId() {
        return productId;
    }

    public long getCount() {
        return count;
    }
}
