package com.loopers.stock.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.stock.domain.vo.StockQuantity;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "product_stock",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_product_stock_product",
        columnNames = "product_id"
    )
)
public class ProductStock extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private StockQuantity quantity;

    private ProductStock(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = StockQuantity.of(quantity);
    }

    public static ProductStock create(Long productId, int quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
        return new ProductStock(productId, quantity);
    }

    public int getQuantity() {
        return quantity.value();
    }

    public boolean hasStock(int quantity) {
        return this.quantity.has(quantity);
    }

    public void deduct(int quantity) {
        this.quantity = this.quantity.deduct(quantity);
    }

    public void changeQuantity(int quantity) {
        this.quantity = StockQuantity.of(quantity);
    }
}
