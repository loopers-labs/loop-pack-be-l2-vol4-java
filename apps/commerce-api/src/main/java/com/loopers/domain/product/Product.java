package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.product.vo.ProductPrice;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    private Long brandId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false))
    private ProductName name;

    private String description;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "price", nullable = false))
    private ProductPrice price;

    private Product(Long brandId, ProductName name, String description, ProductPrice price) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public static Product create(Long brandId, String name, String description, long price) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 비어있을 수 없습니다.");
        }
        validateInfo(description);
        return new Product(brandId, ProductName.of(name), description, ProductPrice.of(price));
    }

    public void update(String name, String description, long price) {
        validateInfo(description);
        this.name = ProductName.of(name);
        this.description = description;
        this.price = ProductPrice.of(price);
    }

    private static void validateInfo(String description) {
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
    }

    public String getName() {
        return name.value();
    }

    public long getPrice() {
        return price.value();
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }
}
