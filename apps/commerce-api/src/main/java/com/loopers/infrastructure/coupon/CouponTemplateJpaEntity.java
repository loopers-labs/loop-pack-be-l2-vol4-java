package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponType;
import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon_templates")
@Getter
public class CouponTemplateJpaEntity extends BaseJpaEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private Long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected CouponTemplateJpaEntity() {}

    CouponTemplateJpaEntity(Long id, String name, CouponType type, Long value, Long minOrderAmount,
            ZonedDateTime expiredAt, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }
}
