package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * Order Aggregate 내부 자식. 주문 시점 상품 정보 스냅샷을 보존한다 (03 §2.5, 04 §2.6).
 * 외부에서 단독 조작 금지 — 항상 OrderModel을 통해 접근.
 * 영속 기술에 의존하지 않는 순수 도메인 객체이며, JPA 매핑은 infrastructure.order.OrderItemEntity가 담당한다.
 */
public class OrderItem {

    private final Long productId;
    private final String productName;
    private final String brandName;
    private final String imageUrl;
    private final Money unitPrice;
    private final Integer quantity;
    private final Money lineTotal;

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
