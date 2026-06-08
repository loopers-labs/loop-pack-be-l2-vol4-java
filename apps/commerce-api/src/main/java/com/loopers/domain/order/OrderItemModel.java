package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.product.vo.StockQuantity;
import com.loopers.support.Guard;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemModel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private OrderModel order;

    @Column(nullable = false)
    private Long stockId;

    @Column(nullable = false)
    private Long productId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "product_name", nullable = false, length = 200))
    private ProductName productName;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "product_price", nullable = false))
    private Price productPrice;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private StockQuantity quantity;

    public OrderItemModel(OrderModel order, Long stockId, Long productId, ProductName productName, Price productPrice, StockQuantity quantity) {
        Guard.notNull(order, "주문은 필수입니다.");
        Guard.notNull(stockId, "재고 ID는 필수입니다.");
        Guard.notNull(productId, "상품 ID는 필수입니다.");
        this.order = order;
        this.stockId = stockId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
    }

    public OrderModel getOrder() { return order; }

    public Long getStockId() { return stockId; }

    public Long getProductId() { return productId; }

    public String getProductName() { return productName.getValue(); }

    public Price getProductPrice() { return productPrice; }

    public StockQuantity getQuantity() { return quantity; }
}
