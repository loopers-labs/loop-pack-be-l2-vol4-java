package com.loopers.infrastructure.jpa;

import com.loopers.domain.common.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Money ↔ BIGINT. 사용처에서 @Convert(converter = MoneyConverter.class)로 명시 적용. */
@Converter
public class MoneyConverter implements AttributeConverter<Money, Long> {

    @Override
    public Long convertToDatabaseColumn(Money attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Money convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : Money.of(dbData);
    }
}
