package com.loopers.inventory.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 상품과 1:1로 연결되지만 독립된 생명주기를 가지는 재고 애그리거트.
 *
 * <p>주문 가능 수량의 확인/차감/복구 책임을 가진다. 동시 주문에서 초과 판매가 발생하지 않도록 저장소 계층에서 비관적 락으로 보호한다.
 */
@Getter
@Entity
@Table(name = "inventory")
public class Inventory extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true, updatable = false)
    private Long productId;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    protected Inventory() {}

    public Inventory(Long productId, Integer availableQuantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품은 필수입니다.");
        }
        if (availableQuantity == null || availableQuantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.productId = productId;
        this.availableQuantity = availableQuantity;
    }

    public boolean isEnough(int quantity) {
        return this.availableQuantity >= quantity;
    }

    /** 재고를 차감한다. 음수가 될 수 없으며, 재고가 부족하면 예외를 던진다. */
    public void deduct(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.availableQuantity < quantity) {
            throw new CoreException(
                ErrorType.CONFLICT,
                "[productId = "
                    + productId
                    + "] 재고가 부족합니다. (보유: "
                    + availableQuantity
                    + ", 요청: "
                    + quantity
                    + ")");
        }
        this.availableQuantity -= quantity;
    }

    /** 차감된 재고를 복구한다. */
    public void restore(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복구 수량은 1 이상이어야 합니다.");
        }
        this.availableQuantity += quantity;
    }

    /** 관리자 재고 수정용. 절대값으로 재고 수량을 설정한다. */
    public void changeQuantity(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.availableQuantity = quantity;
    }
}
