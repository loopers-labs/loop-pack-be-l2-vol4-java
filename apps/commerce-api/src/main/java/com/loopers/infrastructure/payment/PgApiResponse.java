package com.loopers.infrastructure.payment;

/**
 * PG 시뮬레이터의 공통 응답 봉투. 실제 페이로드는 {@code data} 에 담겨 온다.
 * {@code meta} 등 그 외 필드는 역직렬화 시 무시한다.
 */
public record PgApiResponse<T>(T data) {}
