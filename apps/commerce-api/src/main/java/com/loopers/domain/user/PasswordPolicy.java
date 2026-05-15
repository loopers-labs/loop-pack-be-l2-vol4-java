package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class PasswordPolicy {

    private static final Pattern PASSWORD_PATTERN =
        Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\",./<>?]{8,16}$");

    private static final List<DateTimeFormatter> BIRTH_DATE_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("yyyyMMdd"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MMdd"),
        DateTimeFormatter.ofPattern("yyyyMM")
    );

    public void validate(String rawPassword, LocalDate birthDate) {
        validateFormat(rawPassword);
        validateNotContainsBirthDate(rawPassword, birthDate);
    }

    private void validateFormat(String rawPassword) {
        if (rawPassword == null || !PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 사용 가능합니다.");
        }
    }

    private void validateNotContainsBirthDate(String rawPassword, LocalDate birthDate) {
        for (DateTimeFormatter formatter : BIRTH_DATE_FORMATTERS) {
            if (rawPassword.contains(birthDate.format(formatter))) {
                throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
            }
        }
    }
}
