package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class Email {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_LENGTH = 254;

    @Column(name = "email", nullable = false, length = MAX_LENGTH)
    private String value;

    protected Email() {}

    private Email(String value) {
        this.value = value;
    }

    public static Email of(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 254자를 초과할 수 없습니다.");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 올바른 형식이어야 합니다.");
        }
        return new Email(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Email other)) return false;
        return Objects.equals(this.value, other.value);
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
