package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record OrderLine(
    Long productId,
    int quantity,
    String productName,
    Long productPrice,
    String brandName
) {
    public OrderLine {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID 는 비어있을 수 없습니다.");
        }
        if (quantity < 1) {
            throw new CoreException(ErrorType.INVALID_QUANTITY, "주문 수량은 1 이상이어야 합니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명 스냅샷은 비어있을 수 없습니다.");
        }
        if (productPrice == null || productPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격 스냅샷은 0 이상이어야 합니다.");
        }
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명 스냅샷은 비어있을 수 없습니다.");
        }
    }

    public static OrderLine snapshotOf(ProductModel product, BrandModel brand, int quantity) {
        return new OrderLine(
            product.getId(),
            quantity,
            product.getName(),
            product.getPrice(),
            brand.getName()
        );
    }
}
