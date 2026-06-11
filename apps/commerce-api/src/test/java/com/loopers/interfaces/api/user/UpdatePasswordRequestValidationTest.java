package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserCommand;
import com.loopers.interfaces.api.user.UserV1Dto.UpdatePasswordRequest;
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

    private UpdatePasswordRequest validRequest() {
        return new UpdatePasswordRequest("Curr3nt!", "NewPass1!");
    }

    private boolean hasViolationOn(UpdatePasswordRequest request, String property) {
        return validator.validate(request).stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    @DisplayName("유효한 요청이면 위반이 없다")
    void validRequest_hasNoViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    @DisplayName("currentPassword가 누락되면 currentPassword 위반이 발생한다")
    void nullCurrentPassword_hasViolation() {
        UpdatePasswordRequest request = new UpdatePasswordRequest(null, "NewPass1!");

        assertThat(hasViolationOn(request, "currentPassword")).isTrue();
    }

    @Test
    @DisplayName("newPassword가 누락되면 newPassword 위반이 발생한다")
    void nullNewPassword_hasViolation() {
        UpdatePasswordRequest request = new UpdatePasswordRequest("Curr3nt!", null);

        assertThat(hasViolationOn(request, "newPassword")).isTrue();
    }

    @Test
    @DisplayName("새 비밀번호가 형식에 맞지 않으면 newPassword 위반이 발생한다")
    void invalidNewPassword_hasViolation() {
        UpdatePasswordRequest request = new UpdatePasswordRequest("Curr3nt!", "short");

        assertThat(hasViolationOn(request, "newPassword")).isTrue();
    }

    @Test
    @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 위반이 발생한다")
    void newPasswordEqualsCurrent_hasViolation() {
        UpdatePasswordRequest request = new UpdatePasswordRequest("Same1234!", "Same1234!");

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("요청 값을 비밀번호 수정 커맨드로 변환한다")
    void toCommand_returnsChangePasswordCommand() {
        UpdatePasswordRequest request = validRequest();

        UserCommand.ChangePassword command = request.toCommand(1L);

        assertThat(command.userId()).isEqualTo(1L);
        assertThat(command.currentPassword()).isEqualTo("Curr3nt!");
        assertThat(command.newPassword()).isEqualTo("NewPass1!");
    }
}
