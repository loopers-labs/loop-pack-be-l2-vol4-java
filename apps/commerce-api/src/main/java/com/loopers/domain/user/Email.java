package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Objects;
import java.util.regex.Pattern;

public class Email {

    private static final int MAX_LENGTH = 100;

    /**
     * 이메일 형식 정규식
     * - 로컬 파트: 영문/숫자/._%+- 허용 (1자 이상)
     * - @ 정확히 한 번
     * - 도메인 파트: 영문/숫자/- 허용, 점으로 구분된 라벨 형식
     * - TLD: 영문 2자 이상
     * - 전체적으로 ASCII만 허용 (한글 등 비-ASCII 차단)
     */
    private static final Pattern PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$"
    );

    private final String value;

    public Email(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 null이거나 공백일 수 없습니다.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    "이메일은 " + MAX_LENGTH + "자 이하여야 합니다."
            );
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Email)) return false;
        Email email = (Email) o;
        return Objects.equals(value, email.value);
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
