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
    CONCURRENCY_CONFLICT(HttpStatus.CONFLICT, "CONCURRENCY_CONFLICT", "동시 요청이 충돌했습니다. 잠시 후 다시 시도해 주세요."),

    /** 상품 도메인 */
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."),

    /** 브랜드 도메인 */
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "브랜드를 찾을 수 없습니다."),
    DUPLICATE_BRAND_NAME(HttpStatus.CONFLICT, "DUPLICATE_BRAND_NAME", "이미 존재하는 브랜드명입니다."),

    /** 재고 도메인 */
    OUT_OF_STOCK(HttpStatus.CONFLICT, "OUT_OF_STOCK", "재고가 부족합니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "수량은 1 이상이어야 합니다."),

    /** 유저 도메인 */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "유저를 찾을 수 없습니다."),

    /** 주문 도메인 */
    EMPTY_ORDER_ITEMS(HttpStatus.BAD_REQUEST, "EMPTY_ORDER_ITEMS", "주문 항목이 비어있습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    INVALID_ORDER_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_ORDER_PERIOD", "조회 기간이 유효하지 않습니다."),

    /** 쿠폰 도메인 */
    INVALID_COUPON_VALUE(HttpStatus.BAD_REQUEST, "INVALID_COUPON_VALUE", "쿠폰 할인 값이 유효하지 않습니다."),
    COUPON_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_POLICY_NOT_FOUND", "쿠폰 정책을 찾을 수 없습니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "만료된 쿠폰입니다."),
    COUPON_NOT_OWNED(HttpStatus.NOT_FOUND, "COUPON_NOT_OWNED", "쿠폰을 찾을 수 없습니다."),
    COUPON_ALREADY_USED(HttpStatus.CONFLICT, "COUPON_ALREADY_USED", "이미 사용된 쿠폰입니다."),
    COUPON_MIN_ORDER_AMOUNT_NOT_MET(HttpStatus.BAD_REQUEST, "COUPON_MIN_ORDER_AMOUNT_NOT_MET", "쿠폰 최소 주문 금액을 충족하지 못했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
