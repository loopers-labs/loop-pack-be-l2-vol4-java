package com.loopers.domain.coupon;

import com.loopers.domain.money.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 할인 정책 값 객체. 할인 방식(type)과 할인값(value)을 묶어 불변식을 보장한다.
 * - value 는 0보다 커야 한다.
 * - 정률(RATE)은 100%를 넘을 수 없다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Discount {

    private static final long MAX_RATE = 100L;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CouponType type;

    private long value;

    public Discount(CouponType type, long value) {
        validate(type, value);
        this.type = type;
        this.value = value;
    }

    private static void validate(CouponType type, long value) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 비어있을 수 없습니다.");
        }
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인값은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && value > MAX_RATE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인은 100%를 넘을 수 없습니다.");
        }
    }

    public Money apply(Money orderAmount) {
        BigDecimal discountAmount = switch (type) {
            case FIXED -> BigDecimal.valueOf(value);
            case RATE -> orderAmount.getAmount()
                .multiply(BigDecimal.valueOf(value))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        };
        return new Money(discountAmount.min(orderAmount.getAmount()));
    }
}
