package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * 금액 Value Object.
 *
 * <p>한국 원화 기준으로 소수점이 없는 정수만 다룬다. 음수는 허용하지 않으며,
 * 모든 연산(plus/minus/multiply)은 새 인스턴스를 반환하는 불변 객체다.
 *
 * <p>DB 매핑은 {@link Embeddable} 로 처리하여 별도 컬럼 추가 없이
 * {@code bigint} 단일 컬럼에 매핑된다.
 */
@Embeddable
public class Money {

    @Column(nullable = false)
    private Long amount;

    protected Money() {}

    private Money(Long amount) {
        if (amount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 null 일 수 없습니다.");
        }
        if (amount < 0L) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.");
        }
        this.amount = amount;
    }

    public static Money of(long amount) {
        return new Money(amount);
    }

    public static Money zero() {
        return new Money(0L);
    }

    public Money plus(Money other) {
        return new Money(this.amount + other.amount);
    }

    public Money minus(Money other) {
        long result = this.amount - other.amount;
        if (result < 0L) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액 차감 결과가 음수가 될 수 없습니다.");
        }
        return new Money(result);
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액 곱셈 계수는 0 이상이어야 합니다.");
        }
        return new Money(this.amount * factor);
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount >= other.amount;
    }

    public boolean isPositive() {
        return this.amount > 0L;
    }

    public Long getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return Objects.equals(amount, money.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    @Override
    public String toString() {
        return "Money{" + amount + "}";
    }
}
