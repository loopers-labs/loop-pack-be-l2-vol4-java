package com.loopers.stock.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class StockQuantity {

    private int value;

    private StockQuantity(int value) {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.value = value;
    }

    public static StockQuantity of(int value) {
        return new StockQuantity(value);
    }

    public int value() {
        return value;
    }

    public boolean has(int quantity) {
        return value >= quantity;
    }

    public StockQuantity deduct(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (!has(quantity)) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다.");
        }
        return new StockQuantity(value - quantity);
    }
}
