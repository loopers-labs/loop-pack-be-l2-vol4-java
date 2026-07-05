package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZonedDateTime;

/**
 * streamer 가 발급(insert)하는 user_coupon 매핑. 발급요청 이벤트의 ECST 스냅샷을 그대로 행으로 박는다.
 * 할인 계산·사용(use) 같은 무거운 불변식은 commerce-api 의 UserCoupon 이 소유하고,
 * streamer 는 "발급 시점 스냅샷을 물질화(materialize)" 하는 책임만 진다.
 * type/status 는 streamer 가 해석할 필요가 없어 VARCHAR(String) 로 불투명 저장한다(enum 복제 회피).
 */
@Entity
@Table(
    name = "user_coupon",
    uniqueConstraints = @UniqueConstraint(name = "uq_user_coupon_user_policy", columnNames = {"user_id", "coupon_policy_id"})
)
public class UserCoupon extends BaseEntity {

    private static final String STATUS_AVAILABLE = "AVAILABLE";

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_policy_id", nullable = false)
    private Long couponPolicyId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Column(name = "version", nullable = false)
    private long version;

    protected UserCoupon() {}

    private UserCoupon(Long userId, Long couponPolicyId, String type, long discountValue,
                       Long minOrderAmount, ZonedDateTime expiredAt) {
        this.userId = userId;
        this.couponPolicyId = couponPolicyId;
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.status = STATUS_AVAILABLE;
    }

    public static UserCoupon issue(Long userId, Long couponPolicyId, String type, long discountValue,
                                   Long minOrderAmount, ZonedDateTime expiredAt) {
        return new UserCoupon(userId, couponPolicyId, type, discountValue, minOrderAmount, expiredAt);
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponPolicyId() {
        return couponPolicyId;
    }

    public String getType() {
        return type;
    }

    public long getDiscountValue() {
        return discountValue;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public String getStatus() {
        return status;
    }
}
