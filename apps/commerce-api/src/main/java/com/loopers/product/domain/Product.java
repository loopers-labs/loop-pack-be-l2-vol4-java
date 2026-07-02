package com.loopers.product.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "product")
@SQLRestriction("deleted_at is null")
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false, updatable = false)
    private Long brandId;

    private String name;
    private String description;
    private Long price;

    protected Product() {}

    public Product(Long brandId, String name, String description, Long price) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드는 필수입니다.");
        }
        validate(name, description, price);

        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public void update(String newName, String newDescription, Long newPrice) {
        validate(newName, newDescription, newPrice);

        this.name = newName;
        this.description = newDescription;
        this.price = newPrice;
    }

    private static void validate(String name, String description, Long price) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }
}
