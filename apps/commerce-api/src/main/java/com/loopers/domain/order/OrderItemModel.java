package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "order_item")
@SQLRestriction("deleted_at IS NULL")
public class OrderItemModel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrderModel order;

    private Long productId;

    @Embedded
    private ProductSnapshot productSnapshot;

    private Long quantity;

    protected OrderItemModel() {
    }

    public OrderItemModel(Long productId, String productName, BigDecimal productPrice, Long quantity) {
        validate(quantity);
        this.productId = productId;
        this.productSnapshot = new ProductSnapshot(productName, productPrice);
        this.quantity = quantity;
    }

    private void validate(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }

    void assignOrder(OrderModel order) {
        this.order = order;
    }

    public BigDecimal subtotal() {
        return productSnapshot.price().multiply(BigDecimal.valueOf(quantity));
    }
}
