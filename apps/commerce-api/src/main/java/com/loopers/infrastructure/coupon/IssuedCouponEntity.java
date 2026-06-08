package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity(name = "IssuedCoupon")
@Table(name = "issued_coupon",
    uniqueConstraints = @UniqueConstraint(columnNames = {"coupon_id", "user_id"}))
public class IssuedCouponEntity extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    protected IssuedCouponEntity() {}

    public IssuedCouponEntity(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponStatus.AVAILABLE;
    }

    public IssuedCoupon toDomain() {
        return new IssuedCoupon(getId(), couponId, userId, status, usedAt,
            getCreatedAt(), getUpdatedAt(), getDeletedAt());
    }

    public void updateFrom(IssuedCoupon domain) {
        this.status = domain.getStatus();
        this.usedAt = domain.getUsedAt();
    }
}
