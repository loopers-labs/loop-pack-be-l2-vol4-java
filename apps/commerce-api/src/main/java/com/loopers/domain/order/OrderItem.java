package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
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

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    private OrderItem(
        Long brandId,
        String brandName,
        Long productId,
        String productName,
        long unitPrice,
        int quantity
    ) {
        this.brandId = brandId;
        this.brandName = brandName;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.totalPrice = unitPrice * quantity;
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
        validatePrice(unitPrice);
        validateQuantity(quantity);
        return new OrderItem(brandId, brandName, productId, productName, unitPrice, quantity);
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

    private static void validatePrice(long unitPrice) {
        if (unitPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 단가는 0 이상이어야 합니다.");
        }
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }
}
