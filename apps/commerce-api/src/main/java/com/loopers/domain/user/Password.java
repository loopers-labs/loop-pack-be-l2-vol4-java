package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.util.regex.Pattern;

public final class Password {

    private static final Pattern PATTERN = Pattern.compile("^[\\x21-\\x7E]{8,16}$");

    private final String raw;

    private Password(String raw) {
        this.raw = raw;
    }

    public static Password of(String raw, LocalDate birthDate) {
        if (raw == null || !PATTERN.matcher(raw).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문/숫자/특수문자로 구성된 8~16자여야 합니다.");
        }
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        if (containsBirthDate(raw, birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
        return new Password(raw);
    }

    public String raw() {
        return raw;
    }

    private static boolean containsBirthDate(String password, LocalDate birthDate) {
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
