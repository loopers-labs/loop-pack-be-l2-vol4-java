package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
@Getter
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private long likeCount = 0;

    protected Product() {}

    public Product(Long brandId, String name, BigDecimal price) {
        validate(brandId, name, price);
        this.brandId = brandId;
        this.name = name;
        this.price = price;
    }

    public void update(Long newBrandId, String newName, BigDecimal newPrice) {
        validate(newBrandId, newName, newPrice);
        this.brandId = newBrandId;
        this.name = newName;
        this.price = newPrice;
    }

    private void validate(Long brandId, String name, BigDecimal price) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }
}
