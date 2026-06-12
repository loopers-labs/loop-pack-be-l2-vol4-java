package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "coupons")
public class CouponEntity extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    @Column(name = "value", nullable = false)
    private int value;

    @Column(name = "min_order_amount", nullable = false)
    private int minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    private CouponEntity(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static CouponEntity from(CouponModel model) {
        return new CouponEntity(
            model.getName(),
            model.getType(),
            model.getValue(),
            model.getMinOrderAmount(),
            model.getExpiredAt()
        );
    }

    public CouponModel toDomain() {
        return new CouponModel(
            getId(),
            name,
            type,
            value,
            minOrderAmount,
            expiredAt,
            getCreatedAt(),
            getUpdatedAt()
        );
    }
}
