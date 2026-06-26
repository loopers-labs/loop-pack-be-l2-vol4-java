package com.loopers.infrastructure.payment;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * PG 응답 상태코드를 재시도 가능 여부로 분류한다 (Q3 transient vs permanent).
 * - 5xx: 일시적 장애로 보고 RetryableException 으로 변환 → @Retry 대상
 * - 4xx 등: 비즈니스 거절/잘못된 요청이므로 기본 FeignException 그대로 → 재시도 안 함
 */
@Component
public class PgErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() >= 500) {
            return new RetryableException(
                response.status(),
                "PG 일시적 오류 (" + response.status() + ")",
                response.request().httpMethod(),
                (Date) null,
                response.request()
            );
        }
        return defaultDecoder.decode(methodKey, response);
    }
}
