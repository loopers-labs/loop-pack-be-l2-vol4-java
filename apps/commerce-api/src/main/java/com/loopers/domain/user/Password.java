package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class Password {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9\\p{Punct}]{8,16}$");

    @Column(name = "password", nullable = false)
    private String value;

    protected Password() {}

    private Password(String value) {
        this.value = value;
    }

    public static Password of(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대/소문자, 숫자, 특수문자로 8~16자여야 합니다.");
        }
        return new Password(value);
    }

    public static Password encoded(String encodedValue) {
        if (encodedValue == null || encodedValue.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "암호화된 비밀번호는 비어있을 수 없습니다.");
        }
        return new Password(encodedValue);
    }

    public void requireNotContaining(BirthDate birthDate) {
        if (birthDate == null) {
            return;
        }
        if (value.contains(birthDate.formatAsYyyyMmDd())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Password other)) return false;
        return Objects.equals(this.value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "********";
    }
}
