package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.StockQuantity;
import com.loopers.support.Guard;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_stocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductStockModel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductModel product;

    @Embedded
    private Price price;

    @Embedded
    private StockQuantity stockQuantity;

    public ProductStockModel(ProductModel product, Price price, Integer stockQuantity) {
        Guard.notNull(product, "상품은 필수입니다.");
        this.product = product;
        this.price = price;
        this.stockQuantity = new StockQuantity(stockQuantity);
    }

    public void applyPriceTo(long targetPrice) {
        long diff = targetPrice - this.price.getValue();
        if (diff > 0) this.price = this.price.increase(diff);
        else if (diff < 0) this.price = this.price.decrease(-diff);
    }

    public void applyQuantityDelta(int delta) {
        if (delta > 0) this.stockQuantity = this.stockQuantity.increase(delta);
        else if (delta < 0) this.stockQuantity = this.stockQuantity.decrease(-delta);
    }

    public void increase(int amount) {
        this.stockQuantity = this.stockQuantity.increase(amount);
    }

    public void decrease(int amount) {
        this.stockQuantity = this.stockQuantity.decrease(amount);
    }


    public ProductModel getProduct() { return product; }

    public Price getPrice() { return price; }

    public StockQuantity getStockQuantity() { return stockQuantity; }
}
