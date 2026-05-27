package com.loopers.domain.catalog.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class StockQuantity {

    private final Integer quantity;

    public StockQuantity(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }

        this.quantity = quantity;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public boolean isZero() {
        return quantity == 0;
    }

    public StockQuantity decrease(Integer amount) {
        validatePositiveAmount(amount);
        if (quantity < amount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 재고가 부족합니다.");
        }

        return new StockQuantity(quantity - amount);
    }

    public StockQuantity increase(Integer amount) {
        validatePositiveAmount(amount);
        return new StockQuantity(quantity + amount);
    }

    private void validatePositiveAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
    }
}
