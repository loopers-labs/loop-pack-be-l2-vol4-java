package com.loopers.domain.order;

import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

@Embeddable
public class ProductSnapshot {

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    protected ProductSnapshot() {}

    public ProductSnapshot(String productName, String brandName, Money price) {
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        this.productName = productName;
        this.brandName = brandName;
        this.price = price;
    }

    public String getProductName() {
        return productName;
    }

    public String getBrandName() {
        return brandName;
    }

    public Money getPrice() {
        return price;
    }
}
