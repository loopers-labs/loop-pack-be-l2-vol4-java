package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * 수량 Value Object.
 *
 * <p>재고/주문 수량 등 음수가 될 수 없는 정수 값을 표현한다.
 * 모든 연산은 새 인스턴스를 반환하는 불변 객체다.
 *
 * <p>DB 매핑은 {@link Embeddable} 로 처리하여 별도 컬럼 추가 없이
 * {@code int} 단일 컬럼에 매핑된다.
 */
@Embeddable
public class Quantity {

    @Column(nullable = false)
    private int value;

    protected Quantity() {}

    private Quantity(int value) {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 0 이상이어야 합니다.");
        }
        this.value = value;
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    public static Quantity zero() {
        return new Quantity(0);
    }

    public Quantity plus(Quantity other) {
        return new Quantity(this.value + other.value);
    }

    public Quantity minus(Quantity other) {
        int result = this.value - other.value;
        if (result < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량 차감 결과가 음수가 될 수 없습니다.");
        }
        return new Quantity(result);
    }

    public boolean isPositive() {
        return this.value > 0;
    }

    public boolean isGreaterThanOrEqual(Quantity other) {
        return this.value >= other.value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quantity quantity)) return false;
        return value == quantity.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "Quantity{" + value + "}";
    }
}
