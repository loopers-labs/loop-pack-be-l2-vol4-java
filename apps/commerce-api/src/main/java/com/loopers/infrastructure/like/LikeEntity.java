package com.loopers.infrastructure.like;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZonedDateTime;

/**
 * product_like 테이블 JPA 매핑 전용 엔티티. 순수 도메인(LikeModel)과 분리되어 영속 관심사만 담는다.
 * (user_id, product_id) UNIQUE + soft delete(BaseEntity의 deletedAt)로 reactivate 패턴을 지원한다.
 * 도메인 ↔ 엔티티 변환은 LikeEntityMapper가 담당.
 */
@Entity
@Table(
    name = "product_like",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_like", columnNames = {"user_id", "product_id"})
)
public class LikeEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "liked_at", nullable = false)
    private ZonedDateTime likedAt;

    protected LikeEntity() {}

    public LikeEntity(Long userId, Long productId, ZonedDateTime likedAt) {
        this.userId = userId;
        this.productId = productId;
        this.likedAt = likedAt;
    }

    /**
     * 변경 가능한 상태(likedAt)만 갱신한다. userId/productId는 불변.
     * managed 엔티티에 적용 → dirty checking이 UPDATE로 반영.
     * (soft delete 동기화는 BaseEntity.delete()/restore()로 별도 처리)
     */
    public void applyState(ZonedDateTime likedAt) {
        this.likedAt = likedAt;
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
