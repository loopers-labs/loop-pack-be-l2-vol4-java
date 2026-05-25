package com.loopers.domain.product;

import com.loopers.domain.BaseDomain;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Getter
public class Product extends BaseDomain {

    private Long brandId;
    private String name;
    private BigDecimal price;
    private long likeCount = 0;

    public Product(Long brandId, String name, BigDecimal price) {
        validate(brandId, name, price);
        this.brandId = brandId;
        this.name = name;
        this.price = price;
    }

    public Product(Long id, Long brandId, String name, BigDecimal price, long likeCount,
                   ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.likeCount = likeCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public void update(Long newBrandId, String newName, BigDecimal newPrice) {
        validate(newBrandId, newName, newPrice);
        this.brandId = newBrandId;
        this.name = newName;
        this.price = newPrice;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) this.likeCount--;
    }

    private void validate(Long brandId, String name, BigDecimal price) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }
}
