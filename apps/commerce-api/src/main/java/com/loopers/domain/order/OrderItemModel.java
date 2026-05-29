package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.vo.ProductSnapshot;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemModel extends BaseEntity {

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Embedded
    private ProductSnapshot snapshot;

    @Column(nullable = false)
    private int quantity;

    public OrderItemModel(UUID productId, String productName, String brandName, Long price, int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
        this.productId = productId;
        this.snapshot = new ProductSnapshot(productName, brandName, price);
        this.quantity = quantity;
    }

    public String getProductName() { return snapshot.getProductName(); }
    public String getBrandName()   { return snapshot.getBrandName(); }
    public Long getPrice()         { return snapshot.getPrice(); }
    public long getSubtotal()      { return snapshot.getPrice() * quantity; }
}
