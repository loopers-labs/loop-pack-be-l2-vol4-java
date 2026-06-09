package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity(name = "IssuedCoupon")
@Table(name = "issued_coupon")
public class IssuedCouponEntity extends BaseEntity {

    @Version
    private Long version;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected IssuedCouponEntity() {}

    public IssuedCouponEntity(Long couponId, Long userId, ZonedDateTime expiredAt) {
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponStatus.AVAILABLE;
        this.expiredAt = expiredAt;
    }

    public IssuedCoupon toDomain() {
        return new IssuedCoupon(getId(), couponId, userId, status, usedAt,
            expiredAt, getCreatedAt(), getUpdatedAt(), getDeletedAt());
    }

    public void updateFrom(IssuedCoupon domain) {
        this.status = domain.getStatus();
        this.usedAt = domain.getUsedAt();
    }
}
