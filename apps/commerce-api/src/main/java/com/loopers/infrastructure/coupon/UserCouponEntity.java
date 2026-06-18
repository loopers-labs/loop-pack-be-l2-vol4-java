package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.ZonedDateTime;

/**
 * user_coupon 테이블 JPA 매핑 전용 엔티티 (발급분). 상태(AVAILABLE/USED/EXPIRED)는 저장하지 않고
 * used_at + 템플릿 expired_at으로 조회 시점에 파생한다(01 §7.5).
 * version은 낙관적 락(@Version)용 — 동시 use()에서 "정확히 한 번"을 보장한다(UC-20 §5-A).
 * (발급분은 soft delete 대상이 아니며, BaseEntity의 deletedAt 컬럼은 사용하지 않는다 — 04 §2.2.)
 */
@Entity
@Table(name = "user_coupon")
public class UserCouponEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Column(name = "issued_at", nullable = false)
    private ZonedDateTime issuedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected UserCouponEntity() {}

    public UserCouponEntity(Long userId, Long couponId, ZonedDateTime usedAt, ZonedDateTime issuedAt) {
        this.userId = userId;
        this.couponId = couponId;
        this.usedAt = usedAt;
        this.issuedAt = issuedAt;
    }

    /** 변경 가능한 상태는 usedAt뿐 (use/restore). userId/couponId/issuedAt은 불변. */
    public void applyState(ZonedDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public ZonedDateTime getUsedAt() {
        return usedAt;
    }

    public ZonedDateTime getIssuedAt() {
        return issuedAt;
    }
}
