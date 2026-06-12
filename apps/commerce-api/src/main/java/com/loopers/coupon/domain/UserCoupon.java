package com.loopers.coupon.domain;

import com.loopers.common.domain.Money;
import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "user_coupon",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_coupon_coupon_user", columnNames = {"coupon_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    @Column(name = "value", nullable = false)
    private long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private UserCoupon(Long couponId, Long userId, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        this.couponId = couponId;
        this.userId = userId;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.status = CouponStatus.AVAILABLE;
    }

    static UserCoupon issue(Long couponId, Long userId, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return new UserCoupon(couponId, userId, type, value, minOrderAmount, expiredAt);
    }

    public Money calculateDiscount(long orderAmount) {
        return type.discount(value, orderAmount);
    }

    public void use(Long userId, long orderAmount, ZonedDateTime now) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, CouponErrorCode.COUPON_NOT_OWNED);
        }
        if (status == CouponStatus.USED) {
            throw new CoreException(ErrorType.CONFLICT, CouponErrorCode.COUPON_ALREADY_USED);
        }
        if (isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, CouponErrorCode.COUPON_EXPIRED);
        }
        if (minOrderAmount != null && orderAmount < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, CouponErrorCode.COUPON_MIN_ORDER_NOT_MET);
        }
        this.status = CouponStatus.USED;
        this.usedAt = now;
    }

    public void restore() {
        this.status = CouponStatus.AVAILABLE;
        this.usedAt = null;
    }

    public CouponStatus displayStatus(ZonedDateTime now) {
        if (status == CouponStatus.USED) {
            return CouponStatus.USED;
        }
        return isExpired(now) ? CouponStatus.EXPIRED : CouponStatus.AVAILABLE;
    }

    private boolean isExpired(ZonedDateTime now) {
        return expiredAt.isBefore(now);
    }
}
