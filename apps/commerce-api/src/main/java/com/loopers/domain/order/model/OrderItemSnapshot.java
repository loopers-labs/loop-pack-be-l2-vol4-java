package com.loopers.domain.order.model;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "order_item_snapshots")
@Getter
public class OrderItemSnapshot extends BaseEntity {

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "price", nullable = false)
    private Long price;

    protected OrderItemSnapshot() {}

    private OrderItemSnapshot(Long orderItemId, String productName, String brandName, Long price) {
        if (orderItemId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목 ID는 필수입니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
        if (price == null || price <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0보다 커야 합니다.");
        }
        this.orderItemId = orderItemId;
        this.productName = productName;
        this.brandName = brandName;
        this.price = price;
    }

    public static OrderItemSnapshot create(Long orderItemId, String productName, String brandName, Long price) {
        return new OrderItemSnapshot(orderItemId, productName, brandName, price);
    }
}
