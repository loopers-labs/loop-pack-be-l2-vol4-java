package com.loopers.infrastructure.shared;

import com.loopers.domain.shared.Quantity;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Quantity VO 를 단일 컬럼(Integer)으로 매핑한다.
 * autoApply = true 로 모든 Quantity 타입 필드에 자동 적용된다.
 */
@Converter(autoApply = true)
public class QuantityConverter implements AttributeConverter<Quantity, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Quantity quantity) {
        return quantity == null ? null : quantity.value();
    }

    @Override
    public Quantity convertToEntityAttribute(Integer value) {
        return value == null ? null : Quantity.of(value);
    }
}
