package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.common.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItemModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrderModel order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "product_price", nullable = false))
    private Money productPrice;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private Quantity quantity;

    protected OrderItemModel() {}

    public OrderItemModel(OrderModel order, Long productId, String productName, Long productPrice, int quantity) {
        if (productPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 필수입니다.");
        }
        Quantity qty = Quantity.of(quantity);
        if (!qty.isPositive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = Money.of(productPrice);   // Money 내부에서 음수 검증
        this.quantity = qty;
    }

    /**
     * 항목 소계 (Money VO 반환).
     *
     * <p>도메인 내부 협력에서 사용된다 (예: {@code OrderModel.calculateTotalPriceAsMoney}).
     */
    Money calculateSubtotalAsMoney() {
        return productPrice.multiply(quantity.getValue());
    }

    /** 항목 소계 (DTO/응답용 — Long). */
    public Long calculateSubtotal() {
        return calculateSubtotalAsMoney().getAmount();
    }

    public Long getId() {
        return id;
    }

    public OrderModel getOrder() {
        return order;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    /** 상품 가격 (DTO/응답용 — Long). 도메인 내부에서는 {@link Money} 로 캡슐화되어 있다. */
    public Long getProductPrice() {
        return productPrice.getAmount();
    }

    /** 수량 (DTO/응답용 — int). 도메인 내부에서는 {@link Quantity} 로 캡슐화되어 있다. */
    public int getQuantity() {
        return quantity.getValue();
    }
}
