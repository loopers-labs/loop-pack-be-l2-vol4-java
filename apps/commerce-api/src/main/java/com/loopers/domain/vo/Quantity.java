package com.loopers.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * 수량을 표현하는 Value Object.
 * - 불변(immutable). 모든 연산은 새 인스턴스를 반환한다.
 * - 음수 수량은 허용하지 않는다. (0 은 허용 — 재고 0 같은 케이스)
 */
@Embeddable
public class Quantity {

    public static final Quantity ZERO = new Quantity(0);

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

    public int value() {
        return value;
    }

    public Quantity plus(Quantity other) {
        return new Quantity(this.value + other.value);
    }

    public Quantity minus(Quantity other) {
        return new Quantity(this.value - other.value);
    }

    public boolean isGreaterThanOrEqual(Quantity other) {
        return this.value >= other.value;
    }

    public boolean isZero() {
        return this.value == 0;
    }

    public boolean isPositive() {
        return this.value > 0;
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
        return "Quantity(" + value + ")";
    }
}
