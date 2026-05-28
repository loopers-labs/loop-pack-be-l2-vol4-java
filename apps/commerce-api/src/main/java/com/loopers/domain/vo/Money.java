package com.loopers.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * 금액을 표현하는 Value Object.
 * - 불변(immutable). 모든 연산은 새 인스턴스를 반환한다.
 * - 음수 금액은 허용하지 않는다.
 */
@Embeddable
public class Money {

    public static final Money ZERO = new Money(0L);

    @Column(nullable = false)
    private long amount;

    protected Money() {}

    private Money(long amount) {
        if (amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.");
        }
        this.amount = amount;
    }

    public static Money of(long amount) {
        return new Money(amount);
    }

    public long amount() {
        return amount;
    }

    public Money plus(Money other) {
        return new Money(this.amount + other.amount);
    }

    public Money minus(Money other) {
        return new Money(this.amount - other.amount);
    }

    public Money times(int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "곱하는 수량은 0 이상이어야 합니다.");
        }
        return new Money(this.amount * quantity);
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount >= other.amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amount == money.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    @Override
    public String toString() {
        return "Money(" + amount + ")";
    }
}
