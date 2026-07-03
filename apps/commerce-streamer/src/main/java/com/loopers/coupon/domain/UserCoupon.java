package com.loopers.coupon.domain;

import com.loopers.domain.BaseEntity;
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

/**
 * 발급된 사용자 쿠폰. commerce-api 와 같은 user_coupon 테이블을 공유한다.
 * (coupon_id, user_id) 유니크 제약이 중복 발급의 DB 최종 방어선이다.
 */
@Entity
@Table(name = "user_coupon",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_coupon_coupon_user", columnNames = {"coupon_id", "user_id"}))
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

    public static UserCoupon issue(Coupon coupon, Long userId) {
        return new UserCoupon(coupon.getId(), userId, coupon.getType(), coupon.getValue(),
                coupon.getMinOrderAmount(), coupon.getExpiredAt());
    }
}
