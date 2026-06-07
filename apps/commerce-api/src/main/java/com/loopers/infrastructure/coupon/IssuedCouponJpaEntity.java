package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "issued_coupon",
    indexes = {
        @Index(name = "idx_issued_coupon_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_issued_coupon_template_created", columnList = "coupon_template_id, created_at")
    }
)
public class IssuedCouponJpaEntity extends BaseEntity {

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private Long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    protected IssuedCouponJpaEntity() {}

    private IssuedCouponJpaEntity(
        Long couponTemplateId,
        String userId,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        CouponStatus status,
        ZonedDateTime usedAt
    ) {
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.status = status;
        this.usedAt = usedAt;
    }

    public static IssuedCouponJpaEntity from(IssuedCoupon issuedCoupon) {
        return new IssuedCouponJpaEntity(
            issuedCoupon.getCouponTemplateId(),
            issuedCoupon.getUserId(),
            issuedCoupon.getType(),
            issuedCoupon.getValue(),
            issuedCoupon.getMinOrderAmount(),
            issuedCoupon.getExpiredAt(),
            issuedCoupon.getStoredStatus(),
            issuedCoupon.getUsedAt()
        );
    }

    public IssuedCoupon toDomain() {
        return IssuedCoupon.reconstruct(
            getId(),
            couponTemplateId,
            userId,
            type,
            value,
            minOrderAmount,
            expiredAt,
            status,
            usedAt,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(IssuedCoupon issuedCoupon) {
        this.couponTemplateId = issuedCoupon.getCouponTemplateId();
        this.userId = issuedCoupon.getUserId();
        this.type = issuedCoupon.getType();
        this.value = issuedCoupon.getValue();
        this.minOrderAmount = issuedCoupon.getMinOrderAmount();
        this.expiredAt = issuedCoupon.getExpiredAt();
        this.status = issuedCoupon.getStoredStatus();
        this.usedAt = issuedCoupon.getUsedAt();
    }
}
