package com.loopers.domain.product.vo;

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
public class Price {

    @Column(name = "price", nullable = false)
    private Long value;

    public Price(Long value) {
        Guard.notNegative(value, "가격은 0 이상이어야 합니다.");
        this.value = value;
    }

}
