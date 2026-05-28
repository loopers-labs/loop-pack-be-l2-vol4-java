package com.loopers.domain.order.model;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "order_items")
@Getter
public class OrderItem extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderItemStatus status;

    protected OrderItem() {}

    private OrderItem(Long orderId, Long productId, int quantity) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = OrderItemStatus.ORDERED;
    }

    public static OrderItem create(Long orderId, Long productId, int quantity) {
        return new OrderItem(orderId, productId, quantity);
    }

    public void cancel() {
        this.status = OrderItemStatus.CANCELLED;
    }
}
