package com.loopers.product.domain;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class ProductModel extends BaseEntity {

    private String name;
    private String description;
    private Long price;
    private Long brandId;
    private Long likeCount = 0L;

    protected ProductModel() {}

    public ProductModel(String name, String description, Long price, Long brandId) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        this.name = name;
        this.description = description;
        this.price = new Price(price).value();
        this.brandId = brandId;
    }

    public void update(String name, String description, Long price) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        this.name = name;
        this.description = description;
        this.price = new Price(price).value();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getPrice() { return price; }
    public Long getBrandId() { return brandId; }
    public Long getLikeCount() { return likeCount; }
}
