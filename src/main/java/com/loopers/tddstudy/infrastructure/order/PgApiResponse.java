package com.loopers.tddstudy.infrastructure.order;

public record PgApiResponse<T>(
        Meta meta,
        T data
) {
    public record Meta(String result, String errorCode, String message) {}
}
