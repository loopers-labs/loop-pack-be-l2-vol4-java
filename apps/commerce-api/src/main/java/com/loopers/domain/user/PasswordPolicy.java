package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.regex.Pattern;

@Component
public class PasswordPolicy {

    private static final Pattern PATTERN = Pattern.compile("^[\\x21-\\x7E]{8,16}$");

    public void validate(String rawPassword, LocalDate birthDate) {
        if (rawPassword == null || !PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문/숫자/특수문자로 구성된 8~16자여야 합니다.");
        }
        if (containsBirthDate(rawPassword, birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private boolean containsBirthDate(String password, LocalDate birthDate) {
        String year = String.valueOf(birthDate.getYear());
        String yearShort = year.substring(2);
        String month = String.format("%02d", birthDate.getMonthValue());
        String day = String.format("%02d", birthDate.getDayOfMonth());
        return password.contains(year)
            || password.contains(yearShort)
            || password.contains(month)
            || password.contains(day);
    }
}
