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
public class StockQuantity {

    @Column(name = "stock_quantity", nullable = false)
    private Integer value;

    public StockQuantity(Integer value) {
        Guard.notNegative(value, "재고 수량은 0 이상이어야 합니다.");
        this.value = value;
    }

    public StockQuantity increase(int amount) {
        Guard.positive(amount, "증가량은 1 이상이어야 합니다.");
        return new StockQuantity(this.value + amount);
    }

    public StockQuantity decrease(int amount) {
        Guard.positive(amount, "감소량은 1 이상이어야 합니다.");
        if (this.value < amount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        return new StockQuantity(this.value - amount);
    }
}
