package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;

public final class PasswordPolicy {

    private PasswordPolicy() {}

    public static void validate(RawPassword password, LocalDate birthDate) {
        if (password == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        if (containsBirthDate(password.value(), birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private static boolean containsBirthDate(String password, LocalDate birthDate) {
        String year = String.valueOf(birthDate.getYear());
        String yearShort = year.substring(2);
        String month = String.format("%02d", birthDate.getMonthValue());
        String day = String.format("%02d", birthDate.getDayOfMonth());
        return password.contains(year + month + day)
            || password.contains(yearShort + month + day);
    }
}
