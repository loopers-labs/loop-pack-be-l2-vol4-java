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

    /** 회원 관련 에러 */
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "MEMBER-001", "이미 존재하는 아이디입니다."),
    INVALID_LOGIN_ID(HttpStatus.BAD_REQUEST, "MEMBER-002", "로그인 ID 규칙에 맞지 않습니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "MEMBER-003", "비밀번호 규칙에 맞지 않습니다."),
    PASSWORD_CONTAINS_BIRTHDATE(HttpStatus.BAD_REQUEST, "MEMBER-004", "비밀번호에 생년월일을 포함할 수 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER-005", "사용자를 찾을 수 없습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "MEMBER-006", "비밀번호가 일치하지 않습니다."),
    SAME_PASSWORD_AS_OLD(HttpStatus.BAD_REQUEST, "MEMBER-007", "기존 비밀번호와 동일한 비밀번호로 변경할 수 없습니다."),
    INVALID_NAME(HttpStatus.BAD_REQUEST, "MEMBER-008", "이름은 2~20자의 한글 또는 영문이어야 합니다."),
    INVALID_EMAIL(HttpStatus.BAD_REQUEST, "MEMBER-009", "올바른 이메일 형식이 아닙니다."),
    INVALID_BIRTHDATE(HttpStatus.BAD_REQUEST, "MEMBER-010", "생년월일이 유효하지 않습니다."),
    REQUIRED_BIRTHDATE(HttpStatus.BAD_REQUEST, "MEMBER-011", "생년월일을 입력해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
