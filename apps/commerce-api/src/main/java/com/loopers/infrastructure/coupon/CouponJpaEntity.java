package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupons")
@Getter
public class CouponJpaEntity extends BaseJpaEntity {

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    protected CouponJpaEntity() {}

    CouponJpaEntity(Long id, Long couponTemplateId, Long userId, CouponStatus status, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.status = status;
    }
}
