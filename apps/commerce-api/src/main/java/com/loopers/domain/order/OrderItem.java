package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public final class OrderItem {

    private final Long productId;
    private final String productName;
    private final Money unitPrice;
    private final int quantity;

    private OrderItem(Long productId, String productName, Money unitPrice, int quantity) {
        validateProductId(productId);
        validateProductName(productName);
        validateUnitPrice(unitPrice);
        validateQuantity(quantity);
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public static OrderItem of(Long productId, String productName, Money unitPrice, int quantity) {
        return new OrderItem(productId, productName, unitPrice, quantity);
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    private static void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
    }

    private static void validateProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명 스냅샷은 비어있을 수 없습니다.");
        }
    }

    private static void validateUnitPrice(Money unitPrice) {
        if (unitPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "단가 스냅샷은 비어있을 수 없습니다.");
        }
    }

    private static void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}
