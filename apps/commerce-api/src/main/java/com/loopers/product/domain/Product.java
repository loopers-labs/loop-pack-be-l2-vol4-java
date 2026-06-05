package com.loopers.product.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column
    private String thumbnailUrl;

    private Product(Long brandId, String name, String description, long price, String thumbnailUrl) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.thumbnailUrl = thumbnailUrl;
        this.status = ProductStatus.ON_SALE;
        validate();
    }

    public static Product create(Long brandId, String name, String description, long price, String thumbnailUrl) {
        return new Product(brandId, name, description, price, thumbnailUrl);
    }

    public void update(String name, String description, long price, String thumbnailUrl) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.thumbnailUrl = thumbnailUrl;
        validate();
    }

    public void suspend() {
        this.status = ProductStatus.SUSPENDED;
    }

    public void resume() {
        this.status = ProductStatus.ON_SALE;
    }

    public ProductDisplayStatus displayStatus(int stockQuantity) {
        if (status == ProductStatus.SUSPENDED) {
            return ProductDisplayStatus.SUSPENDED;
        }
        if (stockQuantity <= 0) {
            return ProductDisplayStatus.SOLD_OUT;
        }
        return ProductDisplayStatus.ON_SALE;
    }

    private void validate() {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "brandId 는 비어있을 수 없습니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 이름은 비어있을 수 없습니다.");
        }
        if (price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }
}
