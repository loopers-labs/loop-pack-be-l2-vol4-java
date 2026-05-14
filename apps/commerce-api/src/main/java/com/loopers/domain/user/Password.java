package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public class Password {

    private String password;

    private static final String PATTERN = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]{8,16}$";

    protected Password() {}

    private Password(String encodedValue) {
        this.password = encodedValue;
    }

    public static Password of(String raw, PasswordHasher hasher) {
        validate(raw);
        return new Password(hasher.encode(raw));
    }

    public boolean matches(String raw, PasswordHasher hasher) {
        return hasher.matches(raw, password);
    }

    private static void validate(String raw) {
        if (raw == null || !raw.matches(PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자 영문/숫자/특수문자여야 합니다.");
        }
    }
}
