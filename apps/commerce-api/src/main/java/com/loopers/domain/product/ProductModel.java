package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

// 인덱스 컬럼 순서: 동등 조건(brand_id =, deleted_at IS NULL) → 정렬 컬럼(like_count).
// 좋아요순 정렬은 MySQL 8.0 backward index scan 으로 filesort 없이 처리된다.
@Entity
@Table(
    name = "product",
    indexes = {
        @Index(name = "idx_product_brand_deleted_like", columnList = "brand_id, deleted_at, like_count"),
        @Index(name = "idx_product_deleted_like", columnList = "deleted_at, like_count")
    })
public class ProductModel extends BaseEntity {

    private String name;
    private String description;
    private Long price;
    private Integer stock;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    protected ProductModel() {}

    public ProductModel(String name, String description, Long price, Integer stock, Long brandId) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
        if (stock == null || stock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID 는 비어있을 수 없습니다.");
        }

        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.brandId = brandId;
        this.likeCount = 0L;
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

    public Integer getStock() {
        return stock;
    }

    public Long getBrandId() {
        return brandId;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void update(String newName, String newDescription, Long newPrice, Integer newStock, Long newBrandId) {
        if (newName == null || newName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (newDescription == null || newDescription.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (newPrice == null || newPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
        if (newStock == null || newStock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        if (newBrandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID 는 비어있을 수 없습니다.");
        }

        this.name = newName;
        this.description = newDescription;
        this.price = newPrice;
        this.stock = newStock;
        this.brandId = newBrandId;
    }
}
