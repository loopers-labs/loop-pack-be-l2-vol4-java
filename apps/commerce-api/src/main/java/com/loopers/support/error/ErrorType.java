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
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(), "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), "이미 존재하는 리소스입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN.getReasonPhrase(), "접근 권한이 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(), "인증이 필요합니다."),
    PAYMENT_GATEWAY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT_GATEWAY_ERROR", "결제 요청에 실패했습니다."),
    CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN", "결제 시스템이 일시적으로 불안정하여 요청을 차단했습니다. 잠시 후 다시 시도해주세요."),
    PG_QUERY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PG_QUERY_ERROR", "결제 상태 조회에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
