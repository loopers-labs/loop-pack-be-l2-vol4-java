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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
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

    public ProductModel(Long brandId, ProductName name) {
        Guard.notNull(brandId, "브랜드 ID는 필수입니다.");
        Guard.notNull(name, "상품명은 필수입니다.");
        this.brandId = brandId;
        this.name = name;
        this.status = ProductStatus.ACTIVE;
    }

    public void update(ProductName name) {
        Guard.notNull(name, "상품명은 필수입니다.");
        this.name = name;
    }

    public void suspend() {
        this.status = ProductStatus.INACTIVE;
    }

    @Override
    public void delete() {
        super.delete();
        this.status = ProductStatus.INACTIVE;
    }

    public Long getBrandId() { return brandId; }

    public String getName() { return name.getValue(); }

    public ProductStatus getStatus() { return status; }
}
