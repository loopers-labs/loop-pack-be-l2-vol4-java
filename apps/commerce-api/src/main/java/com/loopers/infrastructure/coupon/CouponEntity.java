package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Getter
@Entity(name = "Coupon")
@Table(name = "coupon")
public class CouponEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected CouponEntity() {}

    public CouponEntity(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public Coupon toDomain() {
        return new Coupon(getId(), name, type, value, minOrderAmount, expiredAt,
            getCreatedAt(), getUpdatedAt(), getDeletedAt());
    }

    public void updateFrom(Coupon domain) {
        this.name = domain.getName();
        this.type = domain.getType();
        this.value = domain.getValue();
        this.minOrderAmount = domain.getMinOrderAmount();
        this.expiredAt = domain.getExpiredAt();
        if (domain.getDeletedAt() != null) {
            delete();
        }
    }
}
