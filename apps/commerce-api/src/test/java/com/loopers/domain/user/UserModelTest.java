package com.loopers.domain.user;

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
                .loginId(loginId)
                .encodedPassword(password)
                .name(name)
                .birthDate(birthDate)
                .email(email)
                .build();

            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(loginId),
                () -> assertThat(user.getPassword()).isEqualTo(password),
                () -> assertThat(user.getName()).isEqualTo(name),
                () -> assertThat(user.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(user.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("로그인 ID 가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdIsBlank() {
            // arrange
            String blankLoginId = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserModel.builder()
                    .loginId(blankLoginId)
                    .encodedPassword("Loopers!2026")
                    .name("김성호")
                    .birthDate(LocalDate.of(1993, 11, 3))
                    .email("loopers@example.com")
                    .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID 가 영문/숫자 외의 문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdHasInvalidCharacters() {
            // arrange
            String invalidLoginId = "loopers!";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserModel.builder()
                    .loginId(invalidLoginId)
                    .encodedPassword("Loopers!2026")
                    .name("김성호")
                    .birthDate(LocalDate.of(1993, 11, 3))
                    .email("loopers@example.com")
                    .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 xx@yy.zz 포맷이 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenEmailFormatIsInvalid() {
            // arrange
            String invalidEmail = "loopers#example";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserModel.builder()
                    .loginId("loopers01")
                    .encodedPassword("Loopers!2026")
                    .name("김성호")
                    .birthDate(LocalDate.of(1993, 11, 3))
                    .email(invalidEmail)
                    .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String blankName = "  ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserModel.builder()
                    .loginId("loopers01")
                    .encodedPassword("Loopers!2026")
                    .name(blankName)
                    .birthDate(LocalDate.of(1993, 11, 3))
                    .email("loopers@example.com")
                    .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

    }
}