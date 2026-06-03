package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * Like Aggregate 루트 — 순수 도메인 객체. 멱등 좋아요/취소(reactivate)와 활성 상태 규칙만 보유하고
 * 영속 기술(JPA)에는 의존하지 않는다. JPA 매핑은 infrastructure.like.LikeEntity가 담당하고,
 * 도메인 ↔ 엔티티 변환은 LikeEntityMapper가 처리한다.
 *
 * (user_id, product_id) UNIQUE — 취소 후 재등록은 새 행이 아니라 같은 행을 reactivate한다(04 §4.4).
 */
public class LikeModel {

    private final Long id;   // 영속 전에는 null, 저장 후 매퍼가 채운 값으로 복원된다.
    private final Long userId;
    private final Long productId;
    private ZonedDateTime likedAt;
    private ZonedDateTime deletedAt;   // null이면 활성

    public LikeModel(Long userId, Long productId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 null일 수 없습니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId는 null일 수 없습니다.");
        }
        this.id = null;
        this.userId = userId;
        this.productId = productId;
        this.likedAt = ZonedDateTime.now();
        this.deletedAt = null;
    }

    private LikeModel(Long id, Long userId, Long productId, ZonedDateTime likedAt, ZonedDateTime deletedAt) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.likedAt = likedAt;
        this.deletedAt = deletedAt;
    }

    /** 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용). */
    public static LikeModel reconstitute(Long id, Long userId, Long productId, ZonedDateTime likedAt, ZonedDateTime deletedAt) {
        return new LikeModel(id, userId, productId, likedAt, deletedAt);
    }

    /**
     * 취소된 좋아요 재활성 — deletedAt 복원 + likedAt 갱신. createdAt은 보존된다(같은 행 UPDATE — 04 §4.4).
     */
    public void reactivate() {
        this.deletedAt = null;
        this.likedAt = ZonedDateTime.now();
    }

    /** 좋아요 취소(soft delete). 멱등 — 이미 취소됐으면 시각을 유지한다. */
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /** 활성 여부 — deletedAt이 null이면 활성. */
    public boolean isActive() {
        return deletedAt == null;
    }

    public Long getId() {
        return id;
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

    public ZonedDateTime getDeletedAt() {
        return deletedAt;
    }
}
