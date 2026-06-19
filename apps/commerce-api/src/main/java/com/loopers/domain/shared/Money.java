package com.loopers.domain.shared;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.math.BigDecimal;

/**
 * 금액을 표현하는 값 객체(VO).
 * 값 자체가 의미이며 불변이다. 음수 금액은 허용하지 않는다.
 * JPA 매핑은 MoneyConverter(AttributeConverter)를 통해 단일 컬럼으로 저장된다.
 */
public record Money(BigDecimal amount) {

    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.");
        }
    }

    public static Money zero() {
        return Money.of(0L);
    }

    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public Money plus(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 0 이상이어야 합니다.");
        }
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)));
    }
}
