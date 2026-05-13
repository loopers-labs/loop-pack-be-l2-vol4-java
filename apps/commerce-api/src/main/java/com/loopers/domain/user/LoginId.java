package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Objects;
import java.util.regex.Pattern;

public class LoginId {

    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 20;

    /**
     * 로그인 ID 형식 규칙
     * - 영문 소문자로 시작
     * - 이후 영문 소문자/숫자/특수문자(_, -) 허용
     * - 특수문자 뒤에는 반드시 영문 소문자/숫자가 와야 함
     *   → 연속된 특수문자 금지
     *   → 특수문자로 끝나는 것 금지
     */
    private static final Pattern PATTERN = Pattern.compile("^[a-z]([a-z0-9]|[_-][a-z0-9])*$");

    private final String value;

    public LoginId(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "loginId는 null이거나 공백일 수 없습니다.");
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    "loginId는 " + MIN_LENGTH + "자 이상 " + MAX_LENGTH + "자 이하여야 합니다."
            );
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "loginId 형식이 올바르지 않습니다.");
        }
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginId)) return false;
        LoginId loginId = (LoginId) o;
        return Objects.equals(value, loginId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
