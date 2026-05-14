package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("^[\\x21-\\x7E]+$");

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword, BirthDate birthDate) {
        ensureLength(rawPassword);
        ensureAllowedCharacters(rawPassword);
        ensureNotContainsBirthDate(rawPassword, birthDate);
    }

    private static void ensureLength(String rawPassword) {
        int length = rawPassword == null ? 0 : rawPassword.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.");
        }
    }

    private static void ensureAllowedCharacters(String rawPassword) {
        if (!ALLOWED_CHARACTERS.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대/소문자, 숫자, 특수문자만 사용할 수 있습니다.");
        }
    }

    private static void ensureNotContainsBirthDate(String rawPassword, BirthDate birthDate) {
        for (String pattern : forbiddenBirthDatePatterns(birthDate.getValue())) {
            if (rawPassword.contains(pattern)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
            }
        }
    }

    private static List<String> forbiddenBirthDatePatterns(LocalDate date) {
        String yy = String.format("%02d", date.getYear() % 100);
        String yyyy = String.format("%04d", date.getYear());
        String mm = String.format("%02d", date.getMonthValue());
        String dd = String.format("%02d", date.getDayOfMonth());
        return List.of(
            yy + mm + dd,
            yyyy + mm + dd,
            yy + "-" + mm + "-" + dd,
            yyyy + "-" + mm + "-" + dd,
            yy + "/" + mm + "/" + dd,
            yyyy + "/" + mm + "/" + dd,
            yy + "." + mm + "." + dd,
            yyyy + "." + mm + "." + dd
        );
    }
}
