package com.loopers.coupon.domain;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * commerce-api 와 공유하는 coupon 테이블의 consumer 측 뷰. 발급 판정에 필요한 필드만 둔다.
 * quantity == null 이면 수량 무제한.
 */
@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    @Column(name = "value", nullable = false)
    private long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "quantity")
    private Long quantity;

    @Column(name = "issued_count", nullable = false)
    private long issuedCount;

    private Coupon(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt, Long quantity) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.quantity = quantity;
    }

    public static Coupon createLimited(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt, Long quantity) {
        return new Coupon(name, type, value, minOrderAmount, expiredAt, quantity);
    }
}
