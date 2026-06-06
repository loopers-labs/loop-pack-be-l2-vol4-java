package com.loopers.domain.user;

import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.RawPassword;
import com.loopers.domain.user.vo.UserId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final String DEFAULT_USERID   = "user1";
    private static final String DEFAULT_PASSWORD = "Dlaxodid1!";
    private static final String DEFAULT_NAME     = "홍길동";
    private static final String DEFAULT_BIRTHDAY = "1990-01-01";
    private static final String DEFAULT_EMAIL    = "test@test.com";
    private static final String NEW_PASSWORD     = "Dlaxodid2!";
    private static final String BLANK            = "";
    private static final String SPACE            = " ";

    private UserModel createDefaultUser() {
        return new UserModel(
                new UserId(DEFAULT_USERID),
                new Password(passwordEncoder.encode(DEFAULT_PASSWORD)),
                new Name(DEFAULT_NAME),
                new BirthDay(DEFAULT_BIRTHDAY),
                new Email(DEFAULT_EMAIL),
                UserRole.USER
        );
    }

    @DisplayName("회원 모델을 생성 할 때,")
    @Nested
    class Create {

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("invalidUserIds")
        @DisplayName("아이디가 영문/숫자 외 문자를 포함하거나 16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenUseridIsInvalid(String invalidUserId) {
            CoreException result = assertThrows(CoreException.class, () -> new UserId(invalidUserId));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        static Stream<String> invalidUserIds() {
            return Stream.of("", " ", "유저1", "user_1", "user-1", "user!1", "a".repeat(17));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"a", "abc123", "abcdefghijklmnop"})
        @DisplayName("아이디가 영문/숫자로만 구성되고 16자 이하이면, 유효하다.")
        void createsUserId_whenUseridIsValid(String validUserId) {
            assertThat(new UserId(validUserId).getValue()).isEqualTo(validUserId);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("invalidNames")
        @DisplayName("이름이 공백이거나 2자 미만이거나 50자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenNameIsInvalid(String invalidName) {
            CoreException result = assertThrows(CoreException.class, () -> new Name(invalidName));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        static Stream<String> invalidNames() {
            return Stream.of("", " ", "양", "가".repeat(51));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("validNames")
        @DisplayName("이름이 2자 이상 50자 이하이면, 유효하다.")
        void createsName_whenNameIsValid(String validName) {
            assertThat(new Name(validName).getValue()).isEqualTo(validName);
        }

        static Stream<String> validNames() {
            return Stream.of("홍길", DEFAULT_NAME, "가".repeat(50));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {BLANK, SPACE, "abc1", "toolongpassword12345!", "한글포함패스워드", "dlaxodidAA!", "Dlaxodid11", "12345678!!"})
        @DisplayName("비밀번호가 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenPasswordFormatIsInvalid(String invalidPassword) {
            CoreException result = assertThrows(CoreException.class, () -> new RawPassword(invalidPassword));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {BLANK, SPACE, "1998-04", "19980410"})
        @DisplayName("생년월일 형식이 yyyy-MM-dd가 아니면, BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenBirthDayFormatIsInvalid(String invalidBirthDay) {
            CoreException result = assertThrows(CoreException.class, () -> new BirthDay(invalidBirthDay));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {BLANK, SPACE, "test", "test1234@"})
        @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenEmailFormatIsInvalid(String invalidEmail) {
            CoreException result = assertThrows(CoreException.class, () -> new Email(invalidEmail));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("역할이 null이면, BAD_REQUEST 예외가 발생한다.")
        void throwsBadRequest_whenRoleIsNull() {
            CoreException result = assertThrows(CoreException.class, () ->
                    new UserModel(new UserId(DEFAULT_USERID), new Password(passwordEncoder.encode(DEFAULT_PASSWORD)), new Name(DEFAULT_NAME), new BirthDay(DEFAULT_BIRTHDAY), new Email(DEFAULT_EMAIL), null)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("유효한 입력이면, 회원 모델이 생성된다.")
        void createsUser_whenInputsAreValid() {
            UserModel user = createDefaultUser();

            assertAll(
                    () -> assertThat(user.getUserId().getValue()).isEqualTo(DEFAULT_USERID),
                    () -> assertThat(user.getName().getValue()).isEqualTo(DEFAULT_NAME),
                    () -> assertThat(user.getEmail().getValue()).isEqualTo(DEFAULT_EMAIL),
                    () -> assertThat(user.getBirthDay().getValue()).isEqualTo(DEFAULT_BIRTHDAY),
                    () -> assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, user.getPassword().getValue())).isTrue(),
                    () -> assertThat(user.getRole()).isEqualTo(UserRole.USER)
            );
        }
    }

    @DisplayName("패스워드를 변경 할 때,")
    @Nested
    class ChangePassword {

        @Test
        @DisplayName("새 인코딩된 비밀번호로 변경되면, 비밀번호가 업데이트된다.")
        void changesPassword_whenEncodedPasswordIsGiven() {
            UserModel user = createDefaultUser();
            String newEncoded = passwordEncoder.encode(NEW_PASSWORD);

            user.changePassword(new Password(newEncoded));

            assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPassword().getValue())).isTrue();
        }
    }
}
