package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.vo.OrderPrice;
import com.loopers.domain.order.vo.OrderQuantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "order_items",
    indexes = @Index(name = "idx_order_items_order_id", columnList = "order_id")
)
public class OrderItem extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "unit_price", nullable = false))
    private OrderPrice unitPrice;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private OrderQuantity quantity;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "total_price", nullable = false))
    private OrderPrice totalPrice;

    private OrderItem(
        Long brandId,
        String brandName,
        Long productId,
        String productName,
        OrderPrice unitPrice,
        OrderQuantity quantity
    ) {
        this.brandId = brandId;
        this.brandName = brandName;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.totalPrice = unitPrice.multiply(quantity);
    }

    public static OrderItem create(
        Long brandId,
        String brandName,
        Long productId,
        String productName,
        long unitPrice,
        int quantity
    ) {
        validateSnapshot(brandId, brandName, productId, productName);
        OrderPrice orderPrice = OrderPrice.of(unitPrice);
        OrderQuantity orderQuantity = OrderQuantity.of(quantity);
        return new OrderItem(brandId, brandName, productId, productName, orderPrice, orderQuantity);
    }

    private static void validateSnapshot(Long brandId, String brandName, Long productId, String productName) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 비어있을 수 없습니다.");
        }
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    public long getUnitPrice() {
        return unitPrice.value();
    }

    public int getQuantity() {
        return quantity.value();
    }

    public long getTotalPrice() {
        return totalPrice.value();
    }

    OrderPrice totalPrice() {
        return totalPrice;
    }
}
