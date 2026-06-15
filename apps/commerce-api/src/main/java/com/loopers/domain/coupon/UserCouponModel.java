package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.ZonedDateTime;

/**
 * 유저가 발급받아 소유한 쿠폰(발급분).
 * 템플릿(CouponModel)의 할인 규칙·만료일을 발급 시점에 스냅샷으로 복사해 보관한다.
 * 상태 전이(use)는 자기 자신이 책임진다.
 */
@Entity
@Table(name = "user_coupon")
public class UserCouponModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Embedded
    private DiscountPolicy discountPolicy;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Version
    private Long version;   // 낙관적 락 — 동시 사용 충돌 감지

    protected UserCouponModel() {}

    private UserCouponModel(Long userId, Long couponId, DiscountPolicy discountPolicy, ZonedDateTime expiredAt) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰은 필수입니다.");
        }
        if (discountPolicy == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 정책은 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
        this.userId = userId;
        this.couponId = couponId;
        this.discountPolicy = discountPolicy;
        this.expiredAt = expiredAt;
        this.status = CouponStatus.AVAILABLE;
    }

    public static UserCouponModel issue(Long userId, Long couponId, DiscountPolicy discountPolicy, ZonedDateTime expiredAt) {
        return new UserCouponModel(userId, couponId, discountPolicy, expiredAt);
    }

    /**
     * 쿠폰을 사용한다. AVAILABLE → USED.
     * 이미 사용했으면 CONFLICT, 만료됐으면 BAD_REQUEST.
     */
    public void use(ZonedDateTime now) {
        if (status == CouponStatus.USED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.");
        }
        if (isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
        this.usedAt = now;
    }

    public boolean isExpired(ZonedDateTime now) {
        return now.isAfter(expiredAt);
    }

    public Long getUserId() { return userId; }
    public Long getCouponId() { return couponId; }
    public DiscountPolicy getDiscountPolicy() { return discountPolicy; }
    public ZonedDateTime getExpiredAt() { return expiredAt; }
    public CouponStatus getStatus() { return status; }
    public ZonedDateTime getUsedAt() { return usedAt; }
}
