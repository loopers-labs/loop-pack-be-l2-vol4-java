package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.ZonedDateTime;

/**
 * 발급된 쿠폰 (사용자 소유). 발급 시점 정책 스냅샷을 내장해 자기완결적으로
 * 사용 가능 여부 판단 + 할인 계산을 수행한다. 동시 사용은 @Version 낙관적 락으로 1회만 허용.
 */
@Entity
@Table(name = "user_coupon")
public class UserCoupon extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "name", column = @Column(name = "coupon_name", nullable = false)),
        @AttributeOverride(name = "type", column = @Column(name = "coupon_type", nullable = false)),
        @AttributeOverride(name = "value", column = @Column(name = "discount_value", nullable = false)),
        @AttributeOverride(name = "minOrderAmount", column = @Column(name = "min_order_amount"))
    })
    private CouponSnapshot snapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserCouponStatus status;

    @Column(name = "issued_at", nullable = false)
    private ZonedDateTime issuedAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected UserCoupon() {}

    public UserCoupon(Long userId, Long couponId, CouponSnapshot snapshot, ZonedDateTime issuedAt, ZonedDateTime expiredAt) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (snapshot == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 스냅샷은 필수입니다.");
        }
        if (issuedAt == null || expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급/만료 시각은 필수입니다.");
        }
        this.userId = userId;
        this.couponId = couponId;
        this.snapshot = snapshot;
        this.status = UserCouponStatus.AVAILABLE;
        this.issuedAt = issuedAt;
        this.expiredAt = expiredAt;
    }

    public void use(Long userId, ZonedDateTime now) {
        if (!this.userId.equals(userId)) {
            // 타 유저 쿠폰은 존재를 드러내지 않는다
            throw new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.");
        }
        if (status == UserCouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        if (!expiredAt.isAfter(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = now;
    }

    public Money calculateDiscount(Money orderAmount) {
        return snapshot.calculateDiscount(orderAmount);
    }

    /** 목록 표시용 파생 상태: USED > EXPIRED(파생) > AVAILABLE */
    public UserCouponStatus statusFor(ZonedDateTime now) {
        if (status == UserCouponStatus.USED) {
            return UserCouponStatus.USED;
        }
        if (!expiredAt.isAfter(now)) {
            return UserCouponStatus.EXPIRED;
        }
        return UserCouponStatus.AVAILABLE;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public CouponSnapshot getSnapshot() {
        return snapshot;
    }

    public UserCouponStatus getStatus() {
        return status;
    }

    public ZonedDateTime getIssuedAt() {
        return issuedAt;
    }

    public ZonedDateTime getUsedAt() {
        return usedAt;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }
}
