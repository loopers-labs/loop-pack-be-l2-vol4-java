package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

public class ProductEntity extends BaseEntity {

    private String brandId;
    private String name;
    private String description;
    private Long price;
    private Long likeCount;

    protected ProductEntity() {}

    public ProductEntity(String brandId, String name, String description, Long price) {
        validateBrandId(brandId);
        validateName(name);
        validateDescription(description);
        validatePrice(price);
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.likeCount = 0L;
    }

    public static ProductEntity of(String id, String brandId, String name, String description, Long price, Long likeCount,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        ProductEntity model = new ProductEntity();
        model.brandId = brandId;
        model.name = name;
        model.description = description;
        model.price = price;
        model.likeCount = likeCount;
        model.reconstruct(id, createdAt, updatedAt, deletedAt);
        return model;
    }

    public String getBrandId() {
        return brandId;
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

    public Long getLikeCount() {
        return likeCount;
    }

    public void update(String newName, String newDescription, Long newPrice) {
        validateName(newName);
        validateDescription(newDescription);
        validatePrice(newPrice);
        this.name = newName;
        this.description = newDescription;
        this.price = newPrice;
    }

    private void validateBrandId(String brandId) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (!name.equals(name.strip())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명 앞뒤에 공백을 포함할 수 없습니다.");
        }
        if (name.length() > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 100자를 초과할 수 없습니다.");
        }
    }

    private void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (description.length() > 500) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 500자를 초과할 수 없습니다.");
        }
    }

    private void validatePrice(Long price) {
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }
}
