package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "coupons")
public class CouponModel extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private CouponType type;

    /** FIXED: 할인 금액(원), RATE: 할인율(%) */
    @Column(name = "value", nullable = false)
    private int value;

    @Column(name = "min_order_amount")
    private Integer minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected CouponModel() {}

    public CouponModel(String name, CouponType type, int value, Integer minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public void update(String name, CouponType type, int value, Integer minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiredAt);
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    /**
     * 주문 금액에 대한 할인 금액을 계산한다.
     * FIXED: 고정 금액 (주문 금액 초과 시 주문 금액까지만 할인)
     * RATE: 주문 금액 × 할인율 / 100
     */
    public int calculateDiscount(int orderAmount) {
        if (minOrderAmount != null && orderAmount < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "최소 주문 금액(" + minOrderAmount + "원)을 충족하지 않아 쿠폰을 적용할 수 없습니다.");
        }
        return switch (type) {
            case FIXED -> Math.min(value, orderAmount);
            case RATE -> orderAmount * value / 100;
        };
    }

    private static void validate(String name, CouponType type, int value, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 필수입니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 값은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰의 할인율은 100%를 초과할 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료일은 필수입니다.");
        }
    }
}
