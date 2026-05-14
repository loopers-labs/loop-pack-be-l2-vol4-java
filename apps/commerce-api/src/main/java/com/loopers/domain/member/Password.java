package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.security.crypto.password.PasswordEncoder;

public class Password {

    private final String encodedValue;

    private Password(String encodedValue) {
        this.encodedValue = encodedValue;
    }

    public static Password of(String rawPassword, String birthDate, PasswordEncoder encoder) {
        validate(rawPassword, birthDate);
        return new Password(encoder.encode(rawPassword));
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

    public String getEncodedValue() {
        return encodedValue;
    }
}
