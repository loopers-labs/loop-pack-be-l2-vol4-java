package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.format.DateTimeFormatter;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Password {

    private static final String PASSWORD_PATTERN = "^[A-Za-z0-9\\p{Punct}]{8,16}$";

    private final String value;

    public static Password of(String value, BirthDate birthDate) {
        if (value == null || value.isBlank() || !value.matches(PASSWORD_PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다.");
        }
        if (containsBirthDate(value, birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에는 생년월일을 포함할 수 없습니다.");
        }
        return new Password(value);
    }

    private static boolean containsBirthDate(String value, BirthDate birthDate) {
        String basicBirthDate = birthDate.getValue().format(DateTimeFormatter.BASIC_ISO_DATE);
        String isoBirthDate = birthDate.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE);

        return value.contains(basicBirthDate) || value.contains(isoBirthDate);
    }
}
