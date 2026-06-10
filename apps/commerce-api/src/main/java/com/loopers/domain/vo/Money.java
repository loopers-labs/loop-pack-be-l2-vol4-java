package com.loopers.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;

/**
 * 금액 값 객체. KRW 정수 통화 기준(소수점 없음).
 * 불변이며 모든 연산은 새 인스턴스를 반환한다.
 * 임베드 시 컬럼명은 사용처 엔티티에서 {@code @AttributeOverride} 로 지정한다.
 */
@Embeddable
@Access(AccessType.FIELD)
public final class Money {

    public static final Money ZERO = new Money(0L);

    private long amount;

    protected Money() {}

    public Money(long amount) {
        if (amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.");
        }
        this.amount = amount;
    }

    public static Money of(long amount) {
        return new Money(amount);
    }

    public long getAmount() {
        return amount;
    }

    public Money plus(Money other) {
        if (other == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "더할 금액이 없습니다.");
        }
        return new Money(addExact(this.amount, other.amount));
    }

    public Money times(Quantity quantity) {
        if (quantity == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "곱할 수량이 없습니다.");
        }
        return new Money(multiplyExact(this.amount, quantity.getValue()));
    }

    private static long addExact(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "금액 계산 중 오버플로가 발생했습니다.", e);
        }
    }

    private static long multiplyExact(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "금액 계산 중 오버플로가 발생했습니다.", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Money money = (Money) o;
        return amount == money.amount;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(amount);
    }

    @Override
    public String toString() {
        return "Money{amount=" + amount + "}";
    }
}
