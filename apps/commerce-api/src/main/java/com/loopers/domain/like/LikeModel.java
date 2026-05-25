package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "product_like",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_like", columnNames = {"user_id", "product_id"})
)
public class LikeModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "liked_at", nullable = false)
    private ZonedDateTime likedAt;

    protected LikeModel() {}

    public LikeModel(Long userId, Long productId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 null일 수 없습니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId는 null일 수 없습니다.");
        }
        this.userId = userId;
        this.productId = productId;
        this.likedAt = ZonedDateTime.now();
    }

    /**
     * 취소된 좋아요 재활성 — deletedAt 복원 + likedAt 갱신. createdAt은 보존 (04 §4.4 reactivate 패턴).
     */
    public void reactivate() {
        restore();
        this.likedAt = ZonedDateTime.now();
    }

    /** 활성 여부 — deletedAt이 null이면 활성. */
    public boolean isActive() {
        return getDeletedAt() == null;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public ZonedDateTime getLikedAt() {
        return likedAt;
    }
}
