package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 주문 항목 — 주문 시점의 상품 정보를 **스냅샷**으로 보관한다.
 * Product 가 이후 변경되더라도 과거 주문은 그대로 유지되어야 한다는 비즈니스 요구.
 */
@Entity
@Table(name = "order_items")
public class OrderItemModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", nullable = false, length = 100)
    private String productNameSnapshot;

    @Column(name = "brand_name_snapshot", nullable = false, length = 50)
    private String brandNameSnapshot;

    @Column(name = "price_snapshot", nullable = false)
    private Long priceSnapshot;

    @Column(name = "image_url_snapshot", length = 500)
    private String imageUrlSnapshot;

    @Column(nullable = false)
    private Integer quantity;

    protected OrderItemModel() {}

    public OrderItemModel(
        Long productId,
        String productNameSnapshot,
        String brandNameSnapshot,
        Long priceSnapshot,
        String imageUrlSnapshot,
        Integer quantity
    ) {
        if (productId == null || productId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 양수여야 합니다.");
        }
        if (productNameSnapshot == null || productNameSnapshot.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명 스냅샷은 비어있을 수 없습니다.");
        }
        if (brandNameSnapshot == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명 스냅샷은 null 일 수 없습니다.");
        }
        if (priceSnapshot == null || priceSnapshot < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격 스냅샷은 0 이상이어야 합니다.");
        }
        if (quantity == null || quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.brandNameSnapshot = brandNameSnapshot;
        this.priceSnapshot = priceSnapshot;
        this.imageUrlSnapshot = imageUrlSnapshot;
        this.quantity = quantity;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public String getBrandNameSnapshot() {
        return brandNameSnapshot;
    }

    public Long getPriceSnapshot() {
        return priceSnapshot;
    }

    public String getImageUrlSnapshot() {
        return imageUrlSnapshot;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public long subtotal() {
        return priceSnapshot * quantity;
    }
}
