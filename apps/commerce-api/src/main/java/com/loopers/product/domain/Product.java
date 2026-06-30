package com.loopers.product.domain;

import com.loopers.common.domain.Money;
import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "products",
        indexes = {
                @Index(name = "idx_products_brand_status", columnList = "brand_id, status"),
                @Index(name = "idx_products_like_count", columnList = "like_count DESC, id DESC"),
                @Index(name = "idx_products_brand_like", columnList = "brand_id, like_count DESC, id DESC")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "price", nullable = false))
    private Money price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column
    private String thumbnailUrl;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    private Product(Long brandId, String name, String description, long price, String thumbnailUrl) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = Money.of(price);
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
        this.price = Money.of(price);
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
    }
}
