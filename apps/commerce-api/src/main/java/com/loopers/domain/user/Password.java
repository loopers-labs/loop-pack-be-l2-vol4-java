package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record Password(String value) {
    private static final String PATTERN = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]{8,16}$";

    public static Password of(String rawPassword, BirthDate birthDate, PasswordEncryptor passwordEncryptor) {
        if (rawPassword == null || !rawPassword.matches(PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자 영문/숫자/특수문자여야 합니다.");
        }
        if (rawPassword.contains(birthDate.toCompactString())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }

        return new Password(passwordEncryptor.encrypt(rawPassword));
    }

    public boolean matches(String rawPassword, PasswordEncryptor passwordEncryptor) {
        return passwordEncryptor.matches(rawPassword, this.value);
    }
}
