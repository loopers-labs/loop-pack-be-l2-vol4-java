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
@Table(name = "order_item")
public class OrderItemModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private Quantity quantity;

    // ↓ 스냅샷: 주문 시점의 단가/상품명/브랜드명/이미지 (Product 가 바뀌어도 불변)
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_price", nullable = false))
    private Money unitPrice;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "brand_name", nullable = false, length = 50)
    private String brandName;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    protected OrderItemModel() {}

    private OrderItemModel(Long productId, Quantity quantity, Money unitPrice,
                           String productName, String brandName, String imageUrl) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
        if (quantity == null || !quantity.isPositive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
        if (unitPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "단가는 필수입니다.");
        }
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.productName = productName;
        this.brandName = brandName;
        this.imageUrl = imageUrl;
    }

    public static OrderItemModel of(Long productId, Quantity quantity, Money unitPrice,
                                    String productName, String brandName, String imageUrl) {
        return new OrderItemModel(productId, quantity, unitPrice, productName, brandName, imageUrl);
    }

    /** 이 항목의 소계 = 단가 × 수량 */
    public Money subtotal() {
        return unitPrice.times(quantity.value());
    }

    public Long getProductId() { return productId; }
    public Quantity getQuantity() { return quantity; }
    public Money getUnitPrice() { return unitPrice; }
    public String getProductName() { return productName; }
    public String getBrandName() { return brandName; }
    public String getImageUrl() { return imageUrl; }
}
