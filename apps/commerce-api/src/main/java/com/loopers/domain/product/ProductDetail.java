package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Objects;

/**
 * 상품 조회 응답 조립용 합성체. 재고는 별도 애그리거트(Inventory)라
 * 통째 보관하지 않고 필요한 값(수량·품절 여부)만 평면 추출해 받는다.
 */
public record ProductDetail(Product product, Long brandId, String brandName, long likeCount,
                            int stockQuantity, boolean soldOut) {

    public ProductDetail {
        Objects.requireNonNull(product, "상품 정보가 없습니다.");
        Objects.requireNonNull(brandId, "브랜드 ID가 없습니다.");
        Objects.requireNonNull(brandName, "브랜드 이름이 없습니다.");
        if (likeCount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 음수일 수 없습니다.");
        }
        if (stockQuantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 음수일 수 없습니다.");
        }
    }
}
