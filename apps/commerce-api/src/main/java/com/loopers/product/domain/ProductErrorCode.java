package com.loopers.product.domain;

import com.loopers.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND("PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."),
    STOCK_NOT_FOUND("PRODUCT_STOCK_NOT_FOUND", "상품 재고를 찾을 수 없습니다."),
    OUT_OF_STOCK("PRODUCT_OUT_OF_STOCK", "재고가 부족합니다.");

    private final String code;
    private final String message;
}
