package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 주문 시점의 상품 정보를 스냅샷으로 보관한다.
 * 상품/브랜드가 이후 변경되더라도 주문 당시 합의된 값이 유지된다.
 */
@Getter
@Entity
@Table(name = "order_items")
public class OrderItemModel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private OrderModel order;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private int unitPrice;

    @Column(name = "brand_name", nullable = false, updatable = false)
    private String brandName;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    protected OrderItemModel() {}

    public OrderItemModel(OrderModel order, Long productId, String productName, int unitPrice,
                          String brandName, int quantity) {
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.brandName = brandName;
        this.quantity = quantity;
    }

    public int totalPrice() {
        return unitPrice * quantity;
    }
}
