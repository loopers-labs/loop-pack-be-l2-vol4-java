package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon_policy")
public class CouponPolicy extends BaseEntity {

    @Column(name = "name", nullable = false)
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

    protected CouponPolicy() {}

    public CouponPolicy(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validateName(name);
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 비어있을 수 없습니다.");
        }
        type.validateValue(value);
        validateMinOrderAmount(minOrderAmount);
        validateExpiredAt(expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    /**
     * 쿠폰 정책의 메타 정보(이름·최소주문금액·만료일)를 수정한다.
     * 할인 본질(타입·할인값)은 불변이며, 변경이 필요하면 새 정책을 만든다.
     */
    public void update(String name, Long minOrderAmount, ZonedDateTime expiredAt) {
        validateName(name);
        validateMinOrderAmount(minOrderAmount);
        validateExpiredAt(expiredAt);
        this.name = name;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 정책 이름은 비어있을 수 없습니다.");
        }
    }

    private static void validateMinOrderAmount(Long minOrderAmount) {
        if (minOrderAmount != null && minOrderAmount < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 1 이상이어야 합니다.");
        }
    }

    private static void validateExpiredAt(ZonedDateTime expiredAt) {
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료일은 비어있을 수 없습니다.");
        }
    }

    public boolean isExpired(ZonedDateTime now) {
        return now.isAfter(expiredAt);
    }

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }
}
