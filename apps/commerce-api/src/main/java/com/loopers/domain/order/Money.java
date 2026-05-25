package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Objects;

/**
 * 금액 값 객체. "0 이상" 불변식과 add/multiply 집계 산술을 캡슐화한다 (단위: 원, 단일 통화).
 * 불변 — 연산은 새 Money를 반환. 영속 기술(JPA)에 의존하지 않는 순수 도메인 VO이며,
 * DB 매핑은 OrderEntity/OrderItemEntity가 primitive 컬럼으로 처리한다.
 */
public class Money {

    private final Long amount;

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
