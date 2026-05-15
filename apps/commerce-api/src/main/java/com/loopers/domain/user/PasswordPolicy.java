package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Component
public class PasswordPolicy {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9!@#$%^&*()\\-_+=?]{8,16}$");
    private static final DateTimeFormatter YYYYMMDD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YYMMDD_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    public void validate(String password, LocalDate birth) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문, 숫자, 허용된 특수문자만 사용할 수 있습니다.");
        }
        if (birth == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        if (containsBirthDate(password, birth)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에는 생년월일을 포함할 수 없습니다.");
        }
    }

    private boolean containsBirthDate(String password, LocalDate birth) {
        String yyyyMMdd = birth.format(YYYYMMDD_FORMATTER);
        String yyMMdd = birth.format(YYMMDD_FORMATTER);
        return password.contains(yyyyMMdd) || password.contains(yyMMdd);
    }
}
