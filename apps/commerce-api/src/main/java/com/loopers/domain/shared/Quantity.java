package com.loopers.domain.shared;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 수량을 표현하는 값 객체(VO).
 * 1 이상의 값만 허용한다.
 * JPA 매핑은 QuantityConverter(AttributeConverter)를 통해 단일 컬럼으로 저장된다.
 */
public record Quantity(int value) {

    public Quantity {
        if (value < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }
}
