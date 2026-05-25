package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * 재고 값 객체. "0 이상" 불변식과 차감/복원/가용성 행동을 캡슐화한다.
 * 불변 — 차감/복원은 새 Stock을 반환한다. product 테이블의 stock 컬럼에 embed.
 */
@Embeddable
public class Stock {

    @Column(name = "stock", nullable = false)
    private Integer quantity;

    protected Stock() {}

    public Stock(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.quantity = quantity;
    }

    /** 차감. 부족하면 CONFLICT (04 §4.3). */
    public Stock deduct(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.quantity < amount) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다. (현재 재고: " + this.quantity + ")");
        }
        return new Stock(this.quantity - amount);
    }

    /** 복원 (결제 실패 시 원복 — 01 §7.6). */
    public Stock restore(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복원 수량은 1 이상이어야 합니다.");
        }
        return new Stock(this.quantity + amount);
    }

    public boolean isAvailable(int amount) {
        return this.quantity >= amount;
    }

    public Integer getQuantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Stock)) return false;
        Stock stock = (Stock) o;
        return Objects.equals(quantity, stock.quantity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quantity);
    }
}
