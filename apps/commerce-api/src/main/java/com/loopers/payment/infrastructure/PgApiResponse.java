package com.loopers.payment.infrastructure;

/**
 * pg-simulator 의 공통 응답 래퍼({@code {meta, data}}). 우리 ApiResponse 와 형태만 같고 별개 타입이다.
 */
public record PgApiResponse<T>(Meta meta, T data) {

    public record Meta(String result, String errorCode, String message) {
    }
}
