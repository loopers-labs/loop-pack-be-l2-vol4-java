package com.loopers.domain.user;

import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.EncodedPassword;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.UserName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    @DisplayName("회원 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 정보가 모두 주어지면, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // arrange
            String loginId = "loopers01";
            String password = "Loopers!2026";
            String name = "김성호";
            LocalDate birthDate = LocalDate.of(1993, 11, 3);
            String email = "loopers@example.com";

            // act
            UserModel user = UserModel.builder()
                .loginId(LoginId.of(loginId))
                .password(EncodedPassword.of(password))
                .name(UserName.of(name))
                .birthDate(BirthDate.of(birthDate))
                .email(Email.of(email))
                .build();

            // assert
            assertAll(
                () -> assertThat(user.getLoginId().value()).isEqualTo(loginId),
                () -> assertThat(user.getPassword().value()).isEqualTo(password),
                () -> assertThat(user.getName().value()).isEqualTo(name),
                () -> assertThat(user.getBirthDate().value()).isEqualTo(birthDate),
                () -> assertThat(user.getEmail().value()).isEqualTo(email)
            );
        }

        @DisplayName("이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String blankName = "  ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserModel.builder()
                    .loginId(LoginId.of("loopers01"))
                    .password(EncodedPassword.of("Loopers!2026"))
                    .name(UserName.of(blankName))
                    .birthDate(BirthDate.of(LocalDate.of(1993, 11, 3)))
                    .email(Email.of("loopers@example.com"))
                    .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 인코딩 비밀번호를 주면, password 필드가 새 값으로 갱신된다.")
        @Test
        void changesPassword_whenValidEncodedPasswordIsProvided() {
            // arrange
            UserModel user = UserModel.builder()
                .loginId(LoginId.of("loopers01"))
                .password(EncodedPassword.of("encoded-old-password"))
                .name(UserName.of("김성호"))
                .birthDate(BirthDate.of(LocalDate.of(1993, 11, 3)))
                .email(Email.of("loopers@example.com"))
                .build();
            String newEncodedPassword = "encoded-new-password";

            // act
            user.changePassword(EncodedPassword.of(newEncodedPassword));

            // assert
            assertThat(user.getPassword().value()).isEqualTo(newEncodedPassword);
        }

        @DisplayName("새 인코딩 비밀번호가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewEncodedPasswordIsBlank() {
            // arrange
            UserModel user = UserModel.builder()
                .loginId(LoginId.of("loopers01"))
                .password(EncodedPassword.of("encoded-old-password"))
                .name(UserName.of("김성호"))
                .birthDate(BirthDate.of(LocalDate.of(1993, 11, 3)))
                .email(Email.of("loopers@example.com"))
                .build();
            String blankPassword = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> user.changePassword(EncodedPassword.of(blankPassword)));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
