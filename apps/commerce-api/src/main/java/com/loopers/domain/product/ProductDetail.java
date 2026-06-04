package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Objects;

public record ProductDetail(Product product, Long brandId, String brandName, long likeCount) {

    public ProductDetail {
        Objects.requireNonNull(product, "상품 정보가 없습니다.");
        Objects.requireNonNull(brandId, "브랜드 ID가 없습니다.");
        Objects.requireNonNull(brandName, "브랜드 이름이 없습니다.");
        if (likeCount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 음수일 수 없습니다.");
        }
    }

    public boolean isSoldOut() {
        return product.isSoldOut();
    }
}
