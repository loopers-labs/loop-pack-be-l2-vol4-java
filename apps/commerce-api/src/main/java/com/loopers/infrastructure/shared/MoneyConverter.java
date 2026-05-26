package com.loopers.infrastructure.shared;

import com.loopers.domain.shared.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

/**
 * Money VO 를 단일 컬럼(BigDecimal)으로 매핑한다.
 * autoApply = true 로 모든 Money 타입 필드에 자동 적용되어, 도메인이 인프라에 의존하지 않는다.
 */
@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Money money) {
        return money == null ? null : money.amount();
    }

    @Override
    public Money convertToEntityAttribute(BigDecimal value) {
        return value == null ? null : Money.of(value);
    }
}
