package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class UserModelTest {

    @DisplayName("유저를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenValidInfoProvided() {
            UserModel user = new UserModel("testId", "password1!", "test@email.com", "닉네임");
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo("testId"),
                () -> assertThat(user.getEmail()).isEqualTo("test@email.com"),
                () -> assertThat(user.getNickname()).isEqualTo("닉네임")
            );
        }

        @DisplayName("loginId가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsBlank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new UserModel("", "password1!", "test@email.com", "닉네임"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 유효하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsInvalid() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new UserModel("testId", "password1!", "not-an-email", "닉네임"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("새 비밀번호가 유효하면 변경된다.")
        @Test
        void changesPassword_whenNewPasswordIsValid() {
            UserModel user = new UserModel("testId", "password1!", "test@email.com", "닉네임");
            user.changePassword("newPassword1!");
            assertThat(user.getLoginPw()).isEqualTo("newPassword1!");
        }

        @DisplayName("새 비밀번호가 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsBlank() {
            UserModel user = new UserModel("testId", "password1!", "test@email.com", "닉네임");
            CoreException ex = assertThrows(CoreException.class, () -> user.changePassword(""));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
