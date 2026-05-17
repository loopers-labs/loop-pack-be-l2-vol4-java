package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PasswordValidator {

    public static void validate(String rawPassword, LocalDate birthday) {
        if (rawPassword == null || !rawPassword.matches("^[a-zA-Z0-9\\p{Punct}]{8,16}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 패스워드는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }

        String yyyyMMdd = birthday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String MMdd = birthday.format(DateTimeFormatter.ofPattern("MMdd"));

        if (StringUtils.containsAny(rawPassword, MMdd, yyyyMMdd)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 패스워드에 생년월일을 포함할 수 없습니다.");
        }
    }
}
