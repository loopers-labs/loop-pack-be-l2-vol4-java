package com.loopers.user.domain;

import com.loopers.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    LOGIN_ID_DUPLICATED("USER_LOGIN_ID_DUPLICATED", "이미 사용 중인 로그인 ID입니다."),
    EMAIL_DUPLICATED("USER_EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다."),
    PASSWORD_CONTAINS_BIRTHDATE("USER_PASSWORD_CONTAINS_BIRTHDATE", "비밀번호에 생년월일을 포함할 수 없습니다."),
    CURRENT_PASSWORD_MISMATCH("USER_CURRENT_PASSWORD_MISMATCH", "현재 비밀번호가 일치하지 않습니다.");

    private final String code;
    private final String message;
}
