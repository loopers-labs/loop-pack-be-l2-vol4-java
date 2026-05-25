package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Order(1)
@Component
class PasswordFormatRule implements PasswordRule {

    private static final Pattern PASSWORD_PATTERN =
        Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\",./<>?]{8,16}$");

    @Override
    public void validate(PasswordValidationContext context) {
        String rawPassword = context.rawPassword();
        if (rawPassword == null || !PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 사용 가능합니다.");
        }
    }
}
