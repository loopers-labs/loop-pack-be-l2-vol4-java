package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price_snapshot", nullable = false))
    private Money priceSnapshot;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private Quantity quantity;

    protected OrderItem() {}

    public OrderItem(Long productId, String productNameSnapshot, Money priceSnapshot, Quantity quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
        if (productNameSnapshot == null || productNameSnapshot.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명 스냅샷은 비어있을 수 없습니다.");
        }
        if (priceSnapshot == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격 스냅샷은 필수입니다.");
        }
        if (quantity == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 필수입니다.");
        }
        if (quantity.getValue() < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.priceSnapshot = priceSnapshot;
        this.quantity = quantity;
    }

    public Money subtotal() {
        return priceSnapshot.times(quantity);
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public Long getPriceSnapshot() {
        return priceSnapshot.getAmount();
    }

    public Integer getQuantity() {
        return quantity.getValue();
    }
}
