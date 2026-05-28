package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "product_stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductStockModel extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "stock", nullable = false))
    private Stock stock;

    private ProductStockModel(Long productId, Stock stock) {
        this.productId = productId;
        this.stock = stock;
    }

    public static ProductStockModel of(Long productId, Stock stock) {
        return new ProductStockModel(productId, stock);
    }

    public void changeStock(Stock stock) {
        this.stock = stock;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ProductStockModel that)) {
            return false;
        }

        Long id = getId();
        Long otherId = that.getId();
        if (id == null || id == 0L || otherId == null || otherId == 0L) {
            return false;
        }
        return Objects.equals(id, otherId);
    }

    @Override
    public int hashCode() {
        return ProductStockModel.class.hashCode();
    }
}