package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "product_stock")
public class ProductStockEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    protected ProductStockEntity() {}

    public ProductStockEntity(Long productId, long quantity) {
        this.productId = productId;
        this.quantity = quantity;
        this.updatedAt = ZonedDateTime.now();
    }

    public ProductStock toDomain() {
        return new ProductStock(productId, quantity, updatedAt);
    }

    public static ProductStockEntity from(ProductStock domain) {
        ProductStockEntity entity = new ProductStockEntity(domain.getProductId(), domain.getQuantity());
        entity.updatedAt = domain.getUpdatedAt();
        return entity;
    }
}
