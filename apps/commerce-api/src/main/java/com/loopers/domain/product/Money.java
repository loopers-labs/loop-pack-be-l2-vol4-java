package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public final class Money {

    private final long amount;

    private Money(long amount) {
        if (amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.");
        }
        this.amount = amount;
    }

    public static Money of(long amount) {
        return new Money(amount);
    }

    public Money add(Money other) {
        if (other == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "더할 금액은 필수입니다.");
        }
        try {
            return new Money(Math.addExact(this.amount, other.amount));
        } catch (ArithmeticException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액 합산 중 오버플로우가 발생했습니다.");
        }
    }

    public Money multiply(int multiplier) {
        if (multiplier < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "곱하는 수량은 0 이상이어야 합니다.");
        }
        try {
            return new Money(Math.multiplyExact(this.amount, (long) multiplier));
        } catch (ArithmeticException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액 곱셈 중 오버플로우가 발생했습니다.");
        }
    }

    public boolean isGreaterThanOrEqual(Money other) {
        if (other == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비교 대상 금액은 필수입니다.");
        }
        return this.amount >= other.amount;
    }
}
