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

    /**
     * 두 금액의 차를 반환한다. 결과가 음수가 되면 컴팩트 생성자에서 BAD_REQUEST 가 발생한다.
     * 사용처: 주문 최종 결제 금액(finalAmount = originalAmount - discountAmount) 계산 등.
     */
    public Money minus(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 0 이상이어야 합니다.");
        }
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)));
    }

    /**
     * 금액이 0 인지 판정한다. Money 는 음수를 허용하지 않으므로 isZero 만 두어도 양수 여부 판정에 충분하다.
     */
    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }
}
