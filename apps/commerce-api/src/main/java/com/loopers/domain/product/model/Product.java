package com.loopers.domain.product.model;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
@Getter
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    protected Product() {}

    private Product(Long brandId, String name, String description, Long price) {
        validate(brandId, name, description, price);
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.likeCount = 0;
    }

    public static Product create(Long brandId, String name, String description, Long price) {
        return new Product(brandId, name, description, price);
    }

    public void update(String name, String description, Long price) {
        validate(this.brandId, name, description, price);
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 미만이 될 수 없습니다.");
        }
        this.likeCount--;
    }

    private static void validate(Long brandId, String name, String description, Long price) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (name.length() > 50) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 50자를 초과할 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (description.length() > 200) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 200자를 초과할 수 없습니다.");
        }
        if (price == null || price <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 1 이상이어야 합니다.");
        }
    }
}
