package com.loopers.stock.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "stocks",
    indexes = {
        @Index(name = "idx_stocks_product_id", columnList = "product_id", unique = true)
    }
)
public class StockModel extends BaseEntity {

    private Long productId;
    private Integer totalStock;
    private Integer reservedStock;

    protected StockModel() {}

    public StockModel(Long productId, Integer totalStock) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId는 비어있을 수 없습니다.");
        }
        if (totalStock == null || totalStock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.productId = productId;
        this.totalStock = totalStock;
        this.reservedStock = 0;
    }

    public Integer availableStock() {
        return totalStock - reservedStock;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        if (availableStock() < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.reservedStock += quantity;
    }

    public void confirm(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        if (this.reservedStock < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선점된 재고가 부족합니다.");
        }
        this.totalStock -= quantity;
        this.reservedStock -= quantity;
    }

    public void release(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
        if (this.reservedStock < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선점된 재고가 부족합니다.");
        }
        this.reservedStock -= quantity;
    }

    public Long getProductId() { return productId; }
    public Integer getTotalStock() { return totalStock; }
    public Integer getReservedStock() { return reservedStock; }
}
