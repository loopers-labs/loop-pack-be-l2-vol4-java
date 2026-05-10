package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

public record Password(String value) {

    private static final Pattern ALLOWED = Pattern.compile("^[\\x21-\\x7E]+$");
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;

    public Password {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "비밀번호는 " + MIN_LENGTH + "~" + MAX_LENGTH + "자여야 합니다.");
        }
        if (!ALLOWED.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문/숫자/특수문자만 허용됩니다.");
        }
    }

    public static Password of(String raw, Birth birth) {
        Password password = new Password(raw);
        if (birth != null && raw.contains(birth.asCompact())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
        return password;
    }
}
