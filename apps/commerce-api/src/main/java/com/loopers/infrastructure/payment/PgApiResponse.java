package com.loopers.infrastructure.payment;

/**
 * pg-simulator 의 공통 응답 래퍼. {@code { meta: {...}, data: {...} }} 구조를 역직렬화한다.
 */
public record PgApiResponse<T>(Metadata meta, T data) {

    public record Metadata(String result, String errorCode, String message) {
    }
}
