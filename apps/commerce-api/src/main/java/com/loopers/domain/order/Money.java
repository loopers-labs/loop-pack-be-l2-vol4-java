package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * 금액 값 객체. "0 이상" 불변식과 add/multiply 집계 산술을 캡슐화한다 (단위: 원, 단일 통화).
 * 불변 — 연산은 새 Money를 반환. @Embeddable로 amount 컬럼에 매핑(컬럼명은 @AttributeOverride로 지정).
 */
@Embeddable
public class Money {

    @Column(name = "amount", nullable = false)
    private Long amount;

    protected Money() {}

    public Money(Long amount) {
        if (amount == null || amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.");
        }
        this.amount = amount;
    }

    public static Money zero() {
        return new Money(0L);
    }

    public Money add(Money other) {
        return new Money(this.amount + other.amount);
    }

    /** 수량만큼 곱한다 (lineTotal = unitPrice × quantity). */
    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 0 이상이어야 합니다.");
        }
        return new Money(this.amount * quantity);
    }

    public Long getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money)) return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }
}
