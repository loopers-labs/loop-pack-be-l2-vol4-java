package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon_template")
public class CouponTemplateJpaEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private Long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "max_issues_per_user", nullable = false)
    private Integer maxIssuesPerUser;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected CouponTemplateJpaEntity() {}

    private CouponTemplateJpaEntity(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        Integer maxIssuesPerUser,
        ZonedDateTime expiredAt
    ) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.maxIssuesPerUser = maxIssuesPerUser;
        this.expiredAt = expiredAt;
    }

    public static CouponTemplateJpaEntity from(CouponTemplate couponTemplate) {
        return new CouponTemplateJpaEntity(
            couponTemplate.getName(),
            couponTemplate.getType(),
            couponTemplate.getValue(),
            couponTemplate.getMinOrderAmount(),
            couponTemplate.getMaxIssuesPerUser(),
            couponTemplate.getExpiredAt()
        );
    }

    public CouponTemplate toDomain() {
        return CouponTemplate.reconstruct(
            getId(),
            name,
            type,
            value,
            minOrderAmount,
            maxIssuesPerUser,
            expiredAt,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(CouponTemplate couponTemplate) {
        this.name = couponTemplate.getName();
        this.type = couponTemplate.getType();
        this.value = couponTemplate.getValue();
        this.minOrderAmount = couponTemplate.getMinOrderAmount();
        this.maxIssuesPerUser = couponTemplate.getMaxIssuesPerUser();
        this.expiredAt = couponTemplate.getExpiredAt();
        if (couponTemplate.getDeletedAt() == null) {
            restore();
        } else {
            delete();
        }
    }
}
