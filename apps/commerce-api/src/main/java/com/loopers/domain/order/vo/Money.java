package com.loopers.domain.order.vo;

import com.loopers.support.Guard;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {

    @Column(name = "total_amount", nullable = false)
    private Long value;

    public Money(Long value) {
        Guard.notNegative(value, "금액은 0 이상이어야 합니다.");
        this.value = value;
    }
}