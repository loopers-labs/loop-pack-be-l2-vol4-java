package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.domain.shared.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목. 주문에 담긴 상품 1줄을 표현한다.
 * 주문 시점의 상품명/단가를 스냅샷으로 보관하여, 이후 상품 정보가 바뀌어도 주문 내역은 유지된다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_item")
public class OrderItem extends BaseEntity {

    private Long productId;

    private String productName;

    @Column(name = "unit_price", nullable = false)
    private Money unitPrice;

    @Column(name = "quantity", nullable = false)
    private Quantity quantity;

    @Builder(access = AccessLevel.PRIVATE)
    private OrderItem(Long productId, String productName, Money unitPrice, Quantity quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목에는 상품 정보가 필요합니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목에는 상품명 스냅샷이 필요합니다.");
        }
        if (unitPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목에는 단가 스냅샷이 필요합니다.");
        }
        if (quantity == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목에는 수량이 필요합니다.");
        }
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public static OrderItem of(Long productId, String productName, Money unitPrice, Quantity quantity) {
        return OrderItem.builder()
            .productId(productId)
            .productName(productName)
            .unitPrice(unitPrice)
            .quantity(quantity)
            .build();
    }

    /**
     * 이 항목의 소계(단가 × 수량).
     */
    public Money subtotal() {
        return unitPrice.multiply(quantity.value());
    }
}
