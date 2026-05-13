package com.loopers.domain.user;

import java.util.regex.Pattern;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record EncryptedPassword(
    @Column(name = "encrypted_password", nullable = false, length = 60)
    String value
) {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED_PATTERN = Pattern.compile("[A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{};':\",.<>/?|\\\\~`]+");

    public static EncryptedPassword encrypt(String rawPassword, PasswordEncrypter passwordEncrypter) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수입니다.");
        }

        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("비밀번호는 %d~%d자만 허용됩니다.", MIN_LENGTH, MAX_LENGTH));
        }

        if (!ALLOWED_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문/숫자/특수문자만 허용됩니다.");
        }

        return new EncryptedPassword(passwordEncrypter.encrypt(rawPassword));
    }
}
