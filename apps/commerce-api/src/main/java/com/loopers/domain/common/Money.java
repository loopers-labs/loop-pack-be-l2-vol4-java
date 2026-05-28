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
        return new Money(this.value + other.value);
    }

    public Money multiply(int quantity) {
        return new Money(this.value * quantity);
    }
}
