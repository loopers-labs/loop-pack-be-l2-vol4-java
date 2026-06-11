package com.loopers.order.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 당시 상품 정보를 스냅샷으로 보관하는 Order Aggregate 내부 구성요소.
 * 상품/브랜드를 직접 참조하지 않고 주문 시점 값을 별도로 보관하므로,
 * 이후 상품·브랜드가 변경되거나 삭제되어도 주문 이력은 보존된다.
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    private OrderItem(Long productId, String productName, Long brandId, String brandName, long price, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.brandId = brandId;
        this.brandName = brandName;
        this.price = price;
        this.quantity = quantity;
        validate();
    }

    public static OrderItem create(
            Long productId, String productName, Long brandId, String brandName, long price, int quantity
    ) {
        return new OrderItem(productId, productName, brandId, brandName, price, quantity);
    }

    public void assignOrder(Long orderId) {
        this.orderId = orderId;
    }

    public long subtotal() {
        return price * quantity;
    }

    @Override
    protected void guard() {
        if (orderId == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "주문 항목이 주문에 연결되지 않았습니다.");
        }
    }

    private void validate() {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId 는 비어있을 수 없습니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "brandId 는 비어있을 수 없습니다.");
        }
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
        if (price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}
