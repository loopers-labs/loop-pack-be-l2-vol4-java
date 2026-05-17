package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    private final PasswordEncryptor passwordEncryptor = new FakePasswordEncryptor("encrypted:");

    @DisplayName("유저 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상적으로 생성된다.")
        @Test
        void createsUserModel() {
            // given
            String loginId = "user01";
            String password = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "user@example.com";
            Gender gender = Gender.MALE;

            // when
            UserModel userModel = new UserModel(loginId, password, name, birthDate, email, gender, passwordEncryptor);

            // then
            assertThat(userModel.getLoginId()).isEqualTo(new LoginId(loginId));
            assertThat(userModel.getPassword()).isEqualTo(Password.of(password, new BirthDate(birthDate), passwordEncryptor));
            assertThat(userModel.getName()).isEqualTo(name);
            assertThat(userModel.getBirthDate()).isEqualTo(new BirthDate(birthDate));
            assertThat(userModel.getEmail()).isEqualTo(new Email(email));
            assertThat(userModel.getGender()).isEqualTo(gender);
        }

        @DisplayName("이름이 null 이거나 빈 문자열이면, BAD_REQUEST 예외가 발생한다")
        @NullAndEmptySource
        @ParameterizedTest
        void throwsBadRequestException_whenNameIsNullOrEmpty(String name) {
            // given
            String loginId = "user01";
            String password = "Password1!";
            String birthDate = "1990-01-01";
            String email = "user@example.com";
            Gender gender = Gender.MALE;

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(loginId, password, name, birthDate, email, gender, passwordEncryptor)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("성별이 null 이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequestException_whenGenderIsNull() {
            // given
            String loginId = "user01";
            String rawPassword = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "user@example.com";

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(loginId, rawPassword, name, birthDate, email, null, passwordEncryptor)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호 일치 여부를 확인할 때, ")
    @Nested
    class MatchesPassword {
        @DisplayName("일치하면 true를 반환한다")
        @Test
        void returnTrue_whenPasswordIsMatched() {
            // given
            UserModel userModel = new UserModel("user01", "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor);
            String password = "Password1!";

            // when
            boolean result = userModel.matchesPassword(password, passwordEncryptor);

            // then
            assertThat(result).isEqualTo(true);
        }

        @DisplayName("일치하지 않으면 false를 반환한다")
        @Test
        void returnFalse_whenPasswordIsMatched() {
            // given
            UserModel userModel = new UserModel("user01", "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor);
            String password = "Password2!";

            // when
            boolean result = userModel.matchesPassword(password, passwordEncryptor);

            // then
            assertThat(result).isEqualTo(false);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("정상적으로 변경된다.")
        @Test
        void changePassword() {
            // given
            UserModel userModel = new UserModel("user01", "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor);
            String newPassword = "newPassword1!";

            // when
            userModel.changePassword(newPassword, passwordEncryptor);

            // then
            assertThat(userModel.getPassword()).isEqualTo(Password.of(newPassword, new BirthDate("1990-01-01"), passwordEncryptor));
        }

        @DisplayName("신규 비밀번호가 기존 비밀번호와 같으면 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewPasswordIsMatchedWithOldPassword() {
            // given
            UserModel userModel = new UserModel("user01", "Password1!", "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor);
            String newPassword = "Password1!";

            // when
            CoreException result = assertThrows(CoreException.class, () -> userModel.changePassword(newPassword, passwordEncryptor));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

    }

}
