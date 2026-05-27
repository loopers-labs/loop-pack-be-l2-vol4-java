package com.loopers.user.interfaces.api;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SignUpRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    private UserV1Request.SignUp validRequest() {
        return new UserV1Request.SignUp(
            "loopers01",
            "Passw0rd!",
            "김루퍼",
            LocalDate.of(1995, 3, 21),
            "looper@example.com"
        );
    }

    private boolean hasViolationOn(UserV1Request.SignUp request, String property) {
        return validator.validate(request).stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    @DisplayName("모든 필드가 유효하면 위반이 없다")
    void givenValidRequest_whenValidate_thenHasNoViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    @DisplayName("로그인 ID가 형식에 맞지 않으면 loginId 위반이 발생한다")
    void givenInvalidLoginId_whenValidate_thenHasLoginIdViolation() {
        UserV1Request.SignUp request = new UserV1Request.SignUp(
            "AB", "Passw0rd!", "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        );
        assertThat(hasViolationOn(request, "loginId")).isTrue();
    }

    @Test
    @DisplayName("비밀번호가 형식에 맞지 않으면 password 위반이 발생한다")
    void givenInvalidPassword_whenValidate_thenHasPasswordViolation() {
        UserV1Request.SignUp request = new UserV1Request.SignUp(
            "loopers01", "short", "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        );
        assertThat(hasViolationOn(request, "password")).isTrue();
    }

    @Test
    @DisplayName("이름이 형식에 맞지 않으면 name 위반이 발생한다")
    void givenInvalidName_whenValidate_thenHasNameViolation() {
        UserV1Request.SignUp request = new UserV1Request.SignUp(
            "loopers01", "Passw0rd!", "looper01", LocalDate.of(1995, 3, 21), "looper@example.com"
        );
        assertThat(hasViolationOn(request, "name")).isTrue();
    }

    @Test
    @DisplayName("이메일 형식이 아니면 email 위반이 발생한다")
    void givenInvalidEmail_whenValidate_thenHasEmailViolation() {
        UserV1Request.SignUp request = new UserV1Request.SignUp(
            "loopers01", "Passw0rd!", "김루퍼", LocalDate.of(1995, 3, 21), "invalid-email"
        );
        assertThat(hasViolationOn(request, "email")).isTrue();
    }

    @Test
    @DisplayName("생년월일이 미래이면 birthDate 위반이 발생한다")
    void givenFutureBirthDate_whenValidate_thenHasBirthDateViolation() {
        UserV1Request.SignUp request = new UserV1Request.SignUp(
            "loopers01", "Passw0rd!", "김루퍼", LocalDate.now().plusDays(1), "looper@example.com"
        );
        assertThat(hasViolationOn(request, "birthDate")).isTrue();
    }

    @Test
    @DisplayName("필수 필드가 null이면 각 필드에 위반이 발생한다")
    void givenNullFields_whenValidate_thenHasViolationForEachField() {
        UserV1Request.SignUp request = new UserV1Request.SignUp(null, null, null, null, null);
        assertThat(hasViolationOn(request, "loginId")).isTrue();
        assertThat(hasViolationOn(request, "password")).isTrue();
        assertThat(hasViolationOn(request, "name")).isTrue();
        assertThat(hasViolationOn(request, "email")).isTrue();
        assertThat(hasViolationOn(request, "birthDate")).isTrue();
    }
}
