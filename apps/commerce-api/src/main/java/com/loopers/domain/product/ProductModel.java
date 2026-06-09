package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductModel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private BrandModel brand;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    @Column(name = "like_count", nullable = false)
    private long likeCount = 0L;

    @Column(name = "likes_purged", nullable = false)
    private boolean likesPurged = false;

    public ProductModel(BrandModel brand, String name, String description, Long price) {
        validate(name, description);
        this.brand = brand;
        this.name = name;
        this.description = description;
        this.price = new Money(price);
        this.likeCount = 0L;
        this.likesPurged = false;
    }

    public Long getPrice() {
        return price.getAmount();
    }

    public void update(String newName, String newDescription, Long newPrice) {
        validate(newName, newDescription);
        this.name = newName;
        this.description = newDescription;
        this.price = new Money(newPrice);
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    private void validate(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
    }
}
