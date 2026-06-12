package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 좋아요는 User 와 Product 간의 관계 엔티티.
 * (user_id, product_id) UNIQUE 가 멱등성의 본체 — 동일 사용자가 같은 상품을
 * 두 번 좋아요 해도 DB 레벨에서 단 하나의 행만 존재한다.
 */
@Entity
@Table(
    name = "likes",
    uniqueConstraints = @UniqueConstraint(name = "uk_likes_user_product", columnNames = {"user_id", "product_id"})
)
public class LikeModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    protected LikeModel() {}

    public LikeModel(Long userId, Long productId) {
        if (userId == null || userId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID 는 양수여야 합니다.");
        }
        if (productId == null || productId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 양수여야 합니다.");
        }
        this.userId = userId;
        this.productId = productId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }
}
