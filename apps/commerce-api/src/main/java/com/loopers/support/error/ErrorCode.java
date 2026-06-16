package com.loopers.support.error;

/**
 * 에러 코드 계약. HTTP 상태는 갖지 않고 카테고리({@link ErrorType})로 위임한다.
 * 공통 {@link ErrorType} 과 도메인별 에러 코드 enum(UserErrorCode 등)이 함께 구현한다.
 */
public interface ErrorCode {
    String getCode();
    String getMessage();
}
