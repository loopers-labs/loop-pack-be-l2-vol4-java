package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.springframework.security.crypto.password.PasswordEncoder;


@Embeddable
public class Password {

    @Column(name = "password", nullable = false)
    private String encodedValue;

    protected Password() {}

    private Password(String encodedValue) {
        this.encodedValue = encodedValue;
    }

    public static Password of(String rawPassword, String birthDate, String encodedValue) {
        validate(rawPassword, birthDate);
        return new Password(encodedValue);
    }

    private static void validate(String rawPassword, String birthDate) {
        if (rawPassword == null || rawPassword.length() < 8 || rawPassword.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.");
        }
        if (!rawPassword.matches("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]*$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }
        String birthDateWithoutHyphen = birthDate.replace("-", "");
        if (rawPassword.contains(birthDate) || rawPassword.contains(birthDateWithoutHyphen)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    public boolean matches(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, this.encodedValue);
    }

    public Password change(String oldRaw, String newRaw, String birthDate, String newEncoded, PasswordEncoder encoder) {
        if (!matches(oldRaw, encoder)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "기존 비밀번호가 올바르지 않습니다.");
        }
        if (matches(newRaw, encoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.");
        }
        return Password.of(newRaw, birthDate, newEncoded);
    }
}
