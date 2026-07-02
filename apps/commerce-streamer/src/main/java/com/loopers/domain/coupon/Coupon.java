package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon")
public class Coupon extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;
    @Column(name = "discount_value", nullable = false)
    private long value;
    @Column(name = "min_order_amount")
    private Long minOrderAmount;
    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;
    @Column(name = "quantity")
    private Long quantity;
    @Column(name = "issued_count", nullable = false)
    private long issuedCount;

    protected Coupon() {}

    public CouponSnapshot snapshot() {
        return new CouponSnapshot(name, type, value, minOrderAmount);
    }

    public ZonedDateTime getExpiredAt() { return expiredAt; }
    public Long getQuantity() { return quantity; }
    public long getIssuedCount() { return issuedCount; }
}
