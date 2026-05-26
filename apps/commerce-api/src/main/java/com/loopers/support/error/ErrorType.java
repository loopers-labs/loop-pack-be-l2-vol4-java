package com.loopers.support.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorType {
    /** 범용 에러 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(), "인증에 실패했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(), "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), "이미 존재하는 리소스입니다."),

    /** 상품 도메인 */
    INVALID_LIKE_COUNT(HttpStatus.BAD_REQUEST, "INVALID_LIKE_COUNT", "좋아요 수는 0 미만이 될 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."),

    /** 브랜드 도메인 */
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "브랜드를 찾을 수 없습니다."),
    DUPLICATE_BRAND_NAME(HttpStatus.CONFLICT, "DUPLICATE_BRAND_NAME", "이미 존재하는 브랜드명입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
