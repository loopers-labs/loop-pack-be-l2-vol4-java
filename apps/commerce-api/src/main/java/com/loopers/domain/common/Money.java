package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/** 금액 VO — 음수 금지. 도메인 산술(add/multiply)을 위해 도입. */
public record Money(long value) {

    public static final Money ZERO = new Money(0L);

    public Money {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.");
        }
    }

    public static Money of(Long value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 비어있을 수 없습니다.");
        }
        return new Money(value);
    }

    public Money add(Money other) {
        try {
            return new Money(Math.addExact(this.value, other.value));
        } catch (ArithmeticException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액 합산이 표현 범위를 초과했습니다.");
        }
    }

    public Money multiply(int quantity) {
        try {
            return new Money(Math.multiplyExact(this.value, quantity));
        } catch (ArithmeticException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액 곱셈이 표현 범위를 초과했습니다.");
        }
    }
}
