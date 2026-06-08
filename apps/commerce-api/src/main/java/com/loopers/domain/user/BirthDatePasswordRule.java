package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Order(2)
@Component
class BirthDatePasswordRule implements PasswordRule {

    private static final List<DateTimeFormatter> BIRTH_DATE_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("yyyyMMdd"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MMdd"),
        DateTimeFormatter.ofPattern("yyyyMM")
    );

    @Override
    public void validate(PasswordValidationContext context) {
        String rawPassword = context.rawPassword();
        for (DateTimeFormatter formatter : BIRTH_DATE_FORMATTERS) {
            if (rawPassword.contains(context.birthDate().format(formatter))) {
                throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
            }
        }
    }
}
