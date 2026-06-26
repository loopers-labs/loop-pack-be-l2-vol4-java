package com.loopers.infrastructure.payment;

import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * PG Feign 전용 설정.
 * ⚠ 일부러 @Configuration 을 붙이지 않는다 — 컴포넌트 스캔에 잡히면 모든 Feign 클라이언트의
 *   기본 설정이 되어버린다. @FeignClient(configuration=...) 로만 주입되도록 plain 클래스로 둔다.
 */
public class PgFeignConfig {

    /** 모든 PG 호출에 X-USER-ID 헤더(상점 식별자)를 자동 주입 */
    @Bean
    public RequestInterceptor pgUserIdInterceptor(@Value("${pg.user-id}") String userId) {
        return template -> template.header("X-USER-ID", userId);
    }

    /**
     * HTTP 상태코드로 예외를 분리한다 — CircuitBreaker 의 record/ignore 분기의 핵심.
     * - 5xx → PgServerException (시스템 장애, CB 가 집계)
     * - 4xx → PgClientException (요청/비즈니스 오류, CB 가 무시)
     * (타임아웃·커넥션거부는 HTTP 응답이 아니라 ErrorDecoder 를 안 거치고
     *  feign 예외로 던져지므로, Gateway 에서 PgServerException 으로 래핑한다.)
     */
    @Bean
    public ErrorDecoder pgErrorDecoder() {
        return (String methodKey, Response response) -> {
            int status = response.status();
            String reason = "PG 응답 상태=" + status + " (" + methodKey + ")";
            if (status >= 500) {
                return new PgServerException(reason);
            }
            if (status >= 400) {
                return new PgClientException(reason);
            }
            return new PgServerException("PG 비정상 응답: " + reason);
        };
    }
}
