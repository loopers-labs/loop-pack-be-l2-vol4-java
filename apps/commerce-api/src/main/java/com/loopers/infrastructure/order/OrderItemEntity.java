package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * order_item 테이블 JPA 매핑 전용 엔티티. 주문 시점 상품 스냅샷을 컬럼으로 보존한다.
 * 도메인(OrderItem)과 분리되어 영속 관심사만 담는다.
 */
@Entity
@Table(name = "order_item")
public class OrderItemEntity extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "brand_name", nullable = false, length = 100)
    private String brandName;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "line_total", nullable = false)
    private Long lineTotal;

    protected OrderItemEntity() {}

    public OrderItemEntity(Long productId, String productName, String brandName, String imageUrl,
                           Long unitPrice, Integer quantity, Long lineTotal) {
        this.productId = productId;
        this.productName = productName;
        this.brandName = brandName;
        this.imageUrl = imageUrl;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = lineTotal;
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

    public Long getUnitPrice() {
        return unitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Long getLineTotal() {
        return lineTotal;
    }
}
