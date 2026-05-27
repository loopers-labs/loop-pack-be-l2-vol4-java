package com.loopers.product.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    private Product(Long brandId, String name, String description, long price) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        validate();
    }

    public static Product create(Long brandId, String name, String description, long price) {
        return new Product(brandId, name, description, price);
    }

    public void update(String name, String description, long price) {
        this.name = name;
        this.description = description;
        this.price = price;
        validate();
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
