package com.loopers.interfaces.api.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호에 생년월일이 포함되는 것을 금지하는 클래스 레벨 제약.
 * 비밀번호와 생년월일 두 필드를 함께 봐야 하므로 SignUpRequest 타입에 적용한다.
 */
@Documented
@Constraint(validatedBy = PasswordNotContainBirthDateValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordNotContainBirthDate {
    String message() default "비밀번호에 생년월일을 포함할 수 없습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
