package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 변경된 컬럼만 UPDATE — modify/delete 의 전체 컬럼 UPDATE 가 동시 좋아요 원자 UPDATE 의
// like_count 증감을 stale 값으로 덮어쓰는 lost update 를 막는다(like_count 는 dirty 가 아니므로 UPDATE 에서 빠진다).
@DynamicUpdate
@Table(name = "products", indexes = @Index(name = "idx_products_like_count", columnList = "like_count desc, id desc"))
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    private Product(Long brandId, String name, Money price) {
        validateBrandId(brandId);
        validateName(name);
        validatePrice(price);
        this.brandId = brandId;
        this.name = name;
        this.price = price;
    }

    public static Product create(Long brandId, String name, Money price) {
        return new Product(brandId, name, price);
    }

    public void modify(String name, Money price) {
        validateName(name);
        validatePrice(price);
        this.name = name;
        this.price = price;
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    private void validateBrandId(Long brandId) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    private void validatePrice(Money price) {
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 비어있을 수 없습니다.");
        }
    }
}
