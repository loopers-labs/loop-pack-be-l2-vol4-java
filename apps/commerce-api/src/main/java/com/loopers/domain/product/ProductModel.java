package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.enums.ProductStatus;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.support.Guard;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_brand_name", columnNames = {"brand_id", "name"})
}, indexes = {
        @Index(name = "idx_product_status_deleted_created", columnList = "status, deleted_at, created_at"),
        @Index(name = "idx_product_status_deleted_price", columnList = "status, deleted_at, min_price"),
        @Index(name = "idx_product_status_deleted_likes", columnList = "status, deleted_at, like_count"),
        @Index(name = "idx_product_brand_status_deleted_created", columnList = "brand_id, status, deleted_at, created_at"),
        @Index(name = "idx_product_brand_status_deleted_price", columnList = "brand_id, status, deleted_at, min_price"),
        @Index(name = "idx_product_brand_status_deleted_likes", columnList = "brand_id, status, deleted_at, like_count"),
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductModel extends BaseEntity {

    @Column(nullable = false)
    private Long brandId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false, length = 200))
    private ProductName name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(name = "min_price")
    private Long minPrice;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    public ProductModel(Long brandId, ProductName name) {
        Guard.notNull(brandId, "브랜드 ID는 필수입니다.");
        Guard.notNull(name, "상품명은 필수입니다.");
        this.brandId = brandId;
        this.name = name;
        this.status = ProductStatus.ACTIVE;
        this.likeCount = 0L;
    }

    public void update(ProductName name) {
        Guard.notNull(name, "상품명은 필수입니다.");
        this.name = name;
    }

    @Override
    public void delete() {
        super.delete();
        this.status = ProductStatus.INACTIVE;
    }

    public void updateMinPrice(Long minPrice) {
        this.minPrice = minPrice;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public Long getBrandId() { return brandId; }

    public String getName() { return name.getValue(); }

    public ProductStatus getStatus() { return status; }

    public Long getMinPrice() { return minPrice; }

    public long getLikeCount() { return likeCount; }
}
