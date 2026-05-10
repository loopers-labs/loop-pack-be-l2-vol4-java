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

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_ID_REQUIRED(HttpStatus.BAD_REQUEST, "USER_ID_REQUIRED", "로그인 ID는 필수입니다."),
    USER_ID_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "USER_ID_INVALID_FORMAT", "로그인 ID는 영문과 숫자만 가능합니다."),
    PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "PASSWORD_REQUIRED", "비밀번호는 필수입니다."),
    PASSWORD_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "PASSWORD_INVALID_FORMAT", "비밀번호 형식이 올바르지 않습니다."),
    PASSWORD_CONTAINS_BIRTH_DATE(HttpStatus.BAD_REQUEST, "PASSWORD_CONTAINS_BIRTH_DATE", "생년월일은 비밀번호에 포함할 수 없습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "현재 비밀번호가 일치하지 않습니다."),
    PASSWORD_SAME_AS_CURRENT(HttpStatus.BAD_REQUEST, "PASSWORD_SAME_AS_CURRENT", "현재 비밀번호와 동일합니다."),
    USER_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "USER_NAME_REQUIRED", "이름은 필수입니다."),
    EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "EMAIL_REQUIRED", "이메일은 필수입니다."),
    EMAIL_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "EMAIL_INVALID_FORMAT", "이메일 형식이 올바르지 않습니다."),
    BIRTH_DATE_REQUIRED(HttpStatus.BAD_REQUEST, "BIRTH_DATE_REQUIRED", "생년월일은 필수입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
