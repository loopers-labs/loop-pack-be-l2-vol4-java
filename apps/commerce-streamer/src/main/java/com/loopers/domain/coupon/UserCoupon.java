package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
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
        this.userId = userId;
        this.couponId = couponId;
        this.snapshot = snapshot;
        this.status = UserCouponStatus.AVAILABLE;
        this.issuedAt = issuedAt;
        this.expiredAt = expiredAt;
    }
}
