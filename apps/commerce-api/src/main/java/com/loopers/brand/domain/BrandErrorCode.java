package com.loopers.brand.domain;

import com.loopers.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BrandErrorCode implements ErrorCode {
    BRAND_NOT_FOUND("BRAND_NOT_FOUND", "브랜드를 찾을 수 없습니다."),
    BRAND_NAME_DUPLICATED("BRAND_NAME_DUPLICATED", "이미 존재하는 브랜드명입니다.");

    private final String code;
    private final String message;
}
