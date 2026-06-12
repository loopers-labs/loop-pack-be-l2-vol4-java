package com.loopers.domain.user;

import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.EncodedPassword;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.PlainPassword;
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

class UserTest {

    private static final LocalDate BIRTH = LocalDate.of(1993, 11, 3);

    @DisplayName("회원 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 정보가 모두 주어지면, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenAllFieldsAreValid() {
            // arrange
            String loginId = "loopers01";
            String encodedPassword = "encoded:Loopers!2026";
            String name = "김성호";
            String email = "loopers@example.com";

            // act
            User user = User.signUp(
                LoginId.of(loginId),
                EncodedPassword.of(encodedPassword),
                UserName.of(name),
                BirthDate.of(BIRTH),
                Email.of(email)
            );

            // assert
            assertAll(
                () -> assertThat(user.getLoginId().value()).isEqualTo(loginId),
                () -> assertThat(user.getPassword().value()).isEqualTo(encodedPassword),
                () -> assertThat(user.getName().value()).isEqualTo(name),
                () -> assertThat(user.getBirthDate().value()).isEqualTo(BIRTH),
                () -> assertThat(user.getEmail().value()).isEqualTo(email)
            );
        }

        @DisplayName("이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String blankName = "  ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> User.signUp(
                LoginId.of("loopers01"),
                EncodedPassword.of("encoded:Loopers!2026"),
                UserName.of(blankName),
                BirthDate.of(BIRTH),
                Email.of("loopers@example.com")
            ));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        private final PasswordHasher hasher = new FakePasswordHasher();

        @DisplayName("현재/새 비밀번호가 유효하면, password 필드가 새 인코딩 값으로 갱신된다.")
        @Test
        void updatesPassword_whenInputsAreValid() {
            // arrange
            String currentRaw = "Loopers!2026";
            String newRaw = "NewLoopers!9999";
            User user = userWithPassword(currentRaw);

            // act
            user.changePassword(currentRaw, newRaw, hasher);

            // assert
            assertThat(user.getPassword().value()).isEqualTo("encoded:" + newRaw);
        }

        @DisplayName("현재 비밀번호가 저장된 값과 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorizedException_whenCurrentPasswordDoesNotMatch() {
            // arrange
            User user = userWithPassword("Loopers!2026");

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> user.changePassword("Wrong!9999", "NewLoopers!9999", hasher));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            String samePassword = "Loopers!2026";
            User user = userWithPassword(samePassword);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> user.changePassword(samePassword, samePassword, hasher));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewPasswordContainsBirthDate() {
            // arrange
            String currentRaw = "Loopers!2026";
            String newWithBirth = "Aa!19931103";
            User user = userWithPassword(currentRaw);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> user.changePassword(currentRaw, newWithBirth, hasher));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        private User userWithPassword(String rawPassword) {
            return User.signUp(
                LoginId.of("loopers01"),
                hasher.hash(PlainPassword.of(rawPassword)),
                UserName.of("김성호"),
                BirthDate.of(BIRTH),
                Email.of("loopers@example.com")
            );
        }
    }

    private static class FakePasswordHasher implements PasswordHasher {
        @Override
        public EncodedPassword hash(PlainPassword raw) {
            return EncodedPassword.of("encoded:" + raw.value());
        }

        @Override
        public boolean matches(PlainPassword raw, EncodedPassword encoded) {
            return encoded.value().equals("encoded:" + raw.value());
        }
    }
}