package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserCommand;
import com.loopers.domain.user.User;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignUpRequest(
        @NotBlank(message = "로그인 ID는 필수입니다.")
        @Pattern(regexp = "^[a-z0-9]{4,20}$", message = "로그인 ID는 영문 소문자와 숫자 4~20자여야 합니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(regexp = "^[\\x21-\\x7E]{8,16}$", message = "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Pattern(regexp = "^[가-힣a-zA-Z]{1,10}$", message = "이름은 한글 또는 영문 1~10자여야 합니다.")
        String name,

        @NotNull(message = "생년월일은 필수입니다.")
        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        LocalDate birthDate,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
    ) {
        public UserCommand.SignUp toCommand() {
            return new UserCommand.SignUp(loginId, password, name, birthDate, email);
        }
    }

    public record UserResponse(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getBirthDate(),
                user.getEmail()
            );
        }
    }

    public record UserInfoResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static UserInfoResponse from(User user) {
            return new UserInfoResponse(
                user.getLoginId(),
                maskLastCharacter(user.getName()),
                user.getBirthDate(),
                user.getEmail()
            );
        }

        /** 이름의 마지막 글자를 * 로 마스킹한다. (예: "김루퍼" -> "김루*", "김" -> "*") */
        private static String maskLastCharacter(String name) {
            return name.substring(0, name.length() - 1) + "*";
        }
    }

    public record UpdatePasswordRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Pattern(regexp = "^[\\x21-\\x7E]{8,16}$", message = "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.")
        String newPassword
    ) {

        @AssertTrue(message = "새 비밀번호는 현재 비밀번호와 달라야 합니다.")
        public boolean isNewPasswordDifferent() {
            if (currentPassword == null || newPassword == null) {
                return true;
            }
            return !newPassword.equals(currentPassword);
        }

        public UserCommand.ChangePassword toCommand(Long userId) {
            return new UserCommand.ChangePassword(userId, currentPassword, newPassword);
        }
    }
}
