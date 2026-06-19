package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon")
public class CouponJpaEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private Long value;

    @Column(nullable = false)
    private Long minOrderAmount;

    @Column(nullable = false)
    private ZonedDateTime expiredAt;

    protected CouponJpaEntity() {
    }

    private CouponJpaEntity(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static CouponJpaEntity from(Coupon coupon) {
        return new CouponJpaEntity(
            coupon.getName(),
            coupon.getType(),
            coupon.getValue(),
            coupon.getMinOrderAmount(),
            coupon.getExpiredAt()
        );
    }

    public Coupon toDomain() {
        return Coupon.reconstruct(
            getId(),
            name,
            type,
            value,
            minOrderAmount,
            expiredAt,
            getDeletedAt() != null
        );
    }

    public void update(Coupon coupon) {
        this.name = coupon.getName();
        this.type = coupon.getType();
        this.value = coupon.getValue();
        this.minOrderAmount = coupon.getMinOrderAmount();
        this.expiredAt = coupon.getExpiredAt();
        if (coupon.isDeleted()) {
            delete();
        }
    }

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public Long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }
}
