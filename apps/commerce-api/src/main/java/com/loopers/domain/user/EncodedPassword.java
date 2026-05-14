package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EncodedPassword {

    @Column(name = "encoded_password", nullable = false)
    private String value;

    private EncodedPassword(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수입니다.");
        }
        this.value = value;
    }

    public static EncodedPassword create(PasswordEncoder encoder, String rawPassword) {
        if (encoder == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호 인코더는 필수입니다.");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수입니다.");
        }
        return new EncodedPassword(encoder.encode(rawPassword));
    }

    public boolean matches(String rawPassword, PasswordEncoder encoder) {
        if (rawPassword == null) {
            return false;
        }
        return encoder.matches(rawPassword, this.value);
    }
}
