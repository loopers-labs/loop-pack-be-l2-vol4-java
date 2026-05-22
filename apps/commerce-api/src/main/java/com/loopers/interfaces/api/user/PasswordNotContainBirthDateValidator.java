package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.user.UserV1Dto.SignUpRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.format.DateTimeFormatter;

public class PasswordNotContainBirthDateValidator
    implements ConstraintValidator<PasswordNotContainBirthDate, SignUpRequest> {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    @Override
    public boolean isValid(SignUpRequest request, ConstraintValidatorContext context) {
        // null 은 각 필드의 @NotBlank/@NotNull 이 처리하므로 여기서는 통과시킨다.
        if (request == null || request.password() == null || request.birthDate() == null) {
            return true;
        }
        String password = request.password();
        return !password.contains(request.birthDate().format(YYYYMMDD))
            && !password.contains(request.birthDate().format(YYMMDD));
    }
}
