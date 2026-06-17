package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductModel extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false))
    private ProductName name;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "description", nullable = false))
    private ProductDescription description;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "price", nullable = false))
    private ProductPrice price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    private ProductModel(Long brandId, ProductName name, ProductDescription description, ProductPrice price, ProductStatus status) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.status = status;
        this.likeCount = 0L;
    }

    public static ProductModel of(Long brandId, ProductName name, ProductDescription description, ProductPrice price) {
        return new ProductModel(brandId, name, description, price, ProductStatus.ON_SALE);
    }

    public static ProductModel of(Long brandId, ProductName name, ProductDescription description, ProductPrice price, ProductStatus status) {
        return new ProductModel(brandId, name, description, price, status == null ? ProductStatus.ON_SALE : status);
    }

    public void update(Long brandId, ProductName name, ProductDescription description, ProductPrice price, ProductStatus status) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.status = status == null ? ProductStatus.ON_SALE : status;
    }

    public void changeStatus(ProductStatus status) {
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 상태는 비어있을 수 없습니다.");
        }
        this.status = status;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ProductModel that)) {
            return false;
        }

        Long id = getId();
        Long otherId = that.getId();
        if (id == null || id == 0L || otherId == null || otherId == 0L) {
            return false;
        }
        return Objects.equals(id, otherId);
    }

    @Override
    public int hashCode() {
        return ProductModel.class.hashCode();
    }
}