package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class ProductModel extends BaseEntity {

    private String name;
    private String description;
    private Long price;
    private long likeCount;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    protected ProductModel() {}

    public ProductModel(String name, String description, Long price, Long brandId) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID 는 비어있을 수 없습니다.");
        }

        this.name = name;
        this.description = description;
        this.price = price;
        this.brandId = brandId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Long getPrice() {
        return price;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void update(String newName, String newDescription, Long newPrice) {
        if (newName == null || newName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (newDescription == null || newDescription.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (newPrice == null || newPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }

        this.name = newName;
        this.description = newDescription;
        this.price = newPrice;
    }
}
