package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

import java.util.Optional;
import java.util.regex.Pattern;

@Embeddable
public record LoginId(String value) {

    private static final int MAX_LENGTH = 20;
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{1,%d}$".formatted(MAX_LENGTH));

    public LoginId {
        if (!isValid(value)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID 는 20자 이하의 영문과 숫자만 사용할 수 있습니다.");
        }
    }

    public static LoginId of(String value) {
        return new LoginId(value);
    }

    public static Optional<LoginId> tryParse(String value) {
        return isValid(value) ? Optional.of(of(value)) : Optional.empty();
    }

    private static boolean isValid(String value) {
        return value != null && LOGIN_ID_PATTERN.matcher(value).matches();
    }
}
