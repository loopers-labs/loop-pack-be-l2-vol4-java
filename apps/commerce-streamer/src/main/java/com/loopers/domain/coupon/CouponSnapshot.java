package com.loopers.domain.coupon;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
@Access(AccessType.FIELD)
public class CouponSnapshot {

    private String name;
    @Enumerated(EnumType.STRING)
    private CouponType type;
    private long value;
    private Long minOrderAmount;

    protected CouponSnapshot() {}

    public CouponSnapshot(String name, CouponType type, long value, Long minOrderAmount) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
    }
}
