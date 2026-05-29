package com.loopers.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;

/**
 * 수량 값 객체. 재고/주문 수량 공용. 0 이상 불변식만 보장한다.
 * ("주문 수량 1 이상" 같은 도메인별 규칙은 사용처에서 검증한다.)
 * 불변이며 모든 연산은 새 인스턴스를 반환한다.
 * 임베드 시 컬럼명은 사용처 엔티티에서 {@code @AttributeOverride} 로 지정한다
 * ({@code value} 는 MySQL 예약어이므로 반드시 override 할 것).
 */
@Embeddable
@Access(AccessType.FIELD)
public final class Quantity {

    private int value;

    protected Quantity() {}

    public Quantity(int value) {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 0 이상이어야 합니다.");
        }
        this.value = value;
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    public int getValue() {
        return value;
    }

    public Quantity minus(Quantity other) {
        if (other == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감할 수량이 없습니다.");
        }
        int result = this.value - other.value;
        if (result < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 0 미만이 될 수 없습니다.");
        }
        return new Quantity(result);
    }

    public boolean isGreaterThanOrEqual(Quantity other) {
        if (other == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비교할 수량이 없습니다.");
        }
        return this.value >= other.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Quantity quantity = (Quantity) o;
        return value == quantity.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return "Quantity{value=" + value + "}";
    }
}
