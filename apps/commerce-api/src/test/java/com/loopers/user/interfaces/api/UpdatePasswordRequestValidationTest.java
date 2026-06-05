package com.loopers.user.interfaces.api;

import com.loopers.user.application.UserCommand;
import com.loopers.user.interfaces.api.UserV1Request.UpdatePassword;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdatePasswordRequestValidationTest {

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

    private UpdatePassword validRequest() {
        return new UpdatePassword("Curr3nt!", "NewPass1!");
    }

    private boolean hasViolationOn(UpdatePassword request, String property) {
        return validator.validate(request).stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    @DisplayName("유효한 요청이면 위반이 없다")
    void givenValidRequest_whenValidate_thenHasNoViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    @DisplayName("currentPassword가 누락되면 currentPassword 위반이 발생한다")
    void givenNullCurrentPassword_whenValidate_thenHasViolation() {
        UpdatePassword request = new UpdatePassword(null, "NewPass1!");

        assertThat(hasViolationOn(request, "currentPassword")).isTrue();
    }

    @Test
    @DisplayName("newPassword가 누락되면 newPassword 위반이 발생한다")
    void givenNullNewPassword_whenValidate_thenHasViolation() {
        UpdatePassword request = new UpdatePassword("Curr3nt!", null);

        assertThat(hasViolationOn(request, "newPassword")).isTrue();
    }

    @Test
    @DisplayName("새 비밀번호가 형식에 맞지 않으면 newPassword 위반이 발생한다")
    void givenInvalidNewPassword_whenValidate_thenHasViolation() {
        UpdatePassword request = new UpdatePassword("Curr3nt!", "short");

        assertThat(hasViolationOn(request, "newPassword")).isTrue();
    }

    @Test
    @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 위반이 발생한다")
    void givenNewPasswordEqualsCurrent_whenValidate_thenHasViolation() {
        UpdatePassword request = new UpdatePassword("Same1234!", "Same1234!");

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("요청 값을 비밀번호 수정 커맨드로 변환한다")
    void givenRequest_whenToCommand_thenReturnsChangePasswordCommand() {
        UpdatePassword request = validRequest();

        UserCommand.ChangePassword command = request.toCommand(1L);

        assertThat(command.userId()).isEqualTo(1L);
        assertThat(command.currentPassword()).isEqualTo("Curr3nt!");
        assertThat(command.newPassword()).isEqualTo("NewPass1!");
    }
}
