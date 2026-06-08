package com.loopers.domain.product.vo;

import com.loopers.support.Guard;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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

    public Price increase(long amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "증가량은 1 이상이어야 합니다.");
        }
        return new Price(this.value + amount);
    }

    public Price decrease(long amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "감소량은 1 이상이어야 합니다.");
        }
        if (this.value < amount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "감소량이 현재 가격을 초과할 수 없습니다.");
        }
        return new Price(this.value - amount);
    }
}
