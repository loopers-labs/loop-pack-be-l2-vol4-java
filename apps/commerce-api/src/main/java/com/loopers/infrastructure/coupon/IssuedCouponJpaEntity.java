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

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "issued_coupon",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_issued_coupon_coupon_user",
        columnNames = {"coupon_id", "user_login_id"}
    )
)
public class IssuedCouponJpaEntity extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_login_id", nullable = false)
    private String userLoginId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(nullable = false)
    private ZonedDateTime expiredAt;

    @Column
    private ZonedDateTime usedAt;

    protected IssuedCouponJpaEntity() {
    }

    private IssuedCouponJpaEntity(
        Long couponId,
        String userLoginId,
        CouponStatus status,
        ZonedDateTime expiredAt,
        ZonedDateTime usedAt
    ) {
        this.couponId = couponId;
        this.userLoginId = userLoginId;
        this.status = status;
        this.expiredAt = expiredAt;
        this.usedAt = usedAt;
    }

    public static IssuedCouponJpaEntity from(IssuedCoupon issuedCoupon) {
        return new IssuedCouponJpaEntity(
            issuedCoupon.getCouponId(),
            issuedCoupon.getUserLoginId(),
            issuedCoupon.getStatus(),
            issuedCoupon.getExpiredAt(),
            issuedCoupon.getUsedAt()
        );
    }

    public IssuedCoupon toDomain() {
        return IssuedCoupon.reconstruct(
            getId(),
            couponId,
            userLoginId,
            status,
            expiredAt,
            usedAt
        );
    }

    public void update(IssuedCoupon issuedCoupon) {
        this.couponId = issuedCoupon.getCouponId();
        this.userLoginId = issuedCoupon.getUserLoginId();
        this.status = issuedCoupon.getStatus();
        this.expiredAt = issuedCoupon.getExpiredAt();
        this.usedAt = issuedCoupon.getUsedAt();
    }

    public Long getCouponId() {
        return couponId;
    }

    public String getUserLoginId() {
        return userLoginId;
    }

    public CouponStatus getStatus() {
        return status;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public ZonedDateTime getUsedAt() {
        return usedAt;
    }
}
