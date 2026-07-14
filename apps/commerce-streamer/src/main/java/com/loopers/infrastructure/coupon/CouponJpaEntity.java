package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon")
public class CouponJpaEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private Long value;

    @Column(nullable = false)
    private Long minOrderAmount;

    @Column(nullable = false)
    private ZonedDateTime expiredAt;

    @Column
    private Long issueLimit;

    @Column(nullable = false)
    private Long issuedCount;

    protected CouponJpaEntity() {
    }

    private CouponJpaEntity(
        String name,
        String type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        Long issueLimit
    ) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.issueLimit = issueLimit;
        this.issuedCount = 0L;
    }

    public static CouponJpaEntity firstCome(String name, ZonedDateTime expiredAt, Long issueLimit) {
        return new CouponJpaEntity(name, "FIXED", 1000L, 0L, expiredAt, issueLimit);
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public Long getIssuedCount() {
        return issuedCount;
    }
}
