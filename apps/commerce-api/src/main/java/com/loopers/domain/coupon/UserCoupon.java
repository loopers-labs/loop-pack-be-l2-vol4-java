package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "user_coupon")
public class UserCoupon extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_policy_id", nullable = false)
    private Long couponPolicyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserCouponStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    protected UserCoupon() {}

    private UserCoupon(Long userId, CouponPolicy policy) {
        this.userId = userId;
        this.couponPolicyId = policy.getId();
        this.type = policy.getType();
        this.discountValue = policy.getValue();
        this.minOrderAmount = policy.getMinOrderAmount();
        this.expiredAt = policy.getExpiredAt();
        this.status = UserCouponStatus.AVAILABLE;
    }

    public static UserCoupon issue(Long userId, CouponPolicy policy, ZonedDateTime now) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID 는 비어있을 수 없습니다.");
        }
        if (policy == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 정책은 비어있을 수 없습니다.");
        }
        if (policy.isExpired(now)) {
            throw new CoreException(ErrorType.COUPON_EXPIRED, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        return new UserCoupon(userId, policy);
    }

    public long use(Long requesterId, long orderAmount, ZonedDateTime now) {
        if (!this.userId.equals(requesterId)) {
            throw new CoreException(ErrorType.COUPON_NOT_OWNED, "쿠폰을 찾을 수 없습니다.");
        }
        if (this.status == UserCouponStatus.USED) {
            throw new CoreException(ErrorType.COUPON_ALREADY_USED, "이미 사용된 쿠폰입니다.");
        }
        if (isExpired(now)) {
            throw new CoreException(ErrorType.COUPON_EXPIRED, "만료된 쿠폰입니다.");
        }
        if (!meetsMinOrderAmount(orderAmount)) {
            throw new CoreException(ErrorType.COUPON_MIN_ORDER_AMOUNT_NOT_MET, "쿠폰 최소 주문 금액을 충족하지 못했습니다.");
        }
        this.status = UserCouponStatus.USED;
        this.usedAt = now;
        return type.discount(orderAmount, discountValue);
    }

    private boolean isExpired(ZonedDateTime now) {
        return now.isAfter(expiredAt);
    }

    private boolean meetsMinOrderAmount(long orderAmount) {
        return minOrderAmount == null || orderAmount >= minOrderAmount;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponPolicyId() {
        return couponPolicyId;
    }

    public UserCouponStatus getStatus() {
        return status;
    }

    public ZonedDateTime getUsedAt() {
        return usedAt;
    }
}
