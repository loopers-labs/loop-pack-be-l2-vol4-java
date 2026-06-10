package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Column(name = "quantity", nullable = false)
    private int quantity;

    private Stock(int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.quantity = quantity;
    }

    public static Stock of(int quantity) {
        return new Stock(quantity);
    }

    public boolean hasAtLeast(int qty) {
        if (qty < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비교 수량은 0 이상이어야 합니다.");
        }
        return this.quantity >= qty;
    }

    public Stock decrease(int qty) {
        if (qty <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 0보다 커야 합니다.");
        }
        if (this.quantity < qty) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        return new Stock(this.quantity - qty);
    }

    public Stock adjust(int newQuantity) {
        return new Stock(newQuantity);
    }

    public boolean isSoldOut() {
        return this.quantity == 0;
    }
}
