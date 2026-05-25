package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Order Aggregate 내부 자식 엔티티. 주문 시점 상품 정보 스냅샷을 보존한다 (03 §2.5, 04 §2.6).
 * 외부에서 단독 조작 금지 — 항상 OrderModel을 통해 접근.
 */
@Entity
@Table(name = "order_item")
public class OrderItem extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "brand_name", nullable = false, length = 100)
    private String brandName;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_price", nullable = false))
    private Money unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "line_total", nullable = false))
    private Money lineTotal;

    protected OrderItem() {}

    public OrderItem(Long productId, String productName, String brandName, String imageUrl, Money unitPrice, int quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId는 null일 수 없습니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명 스냅샷은 비어있을 수 없습니다.");
        }
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명 스냅샷은 비어있을 수 없습니다.");
        }
        if (unitPrice == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "단가는 null일 수 없습니다.");
        }
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        this.productId = productId;
        this.productName = productName;
        this.brandName = brandName;
        this.imageUrl = imageUrl;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(quantity);
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getBrandName() {
        return brandName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Money getLineTotal() {
        return lineTotal;
    }
}
