package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Name {

    private static final Pattern ALLOWED = Pattern.compile("^[가-힣a-zA-Z ]+$");
    private static final int MAX_LENGTH = 50;
    private static final char MASK = '*';

    private String value;

    public Name(String value) {
        validate(value);
        this.value = value;
    }

    private static void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "이름은 " + MAX_LENGTH + "자를 초과할 수 없습니다.");
        }
        if (!ALLOWED.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글/영문/공백만 허용됩니다.");
        }
    }

    public String masked() {
        return value.substring(0, value.length() - 1) + MASK;
    }
}
