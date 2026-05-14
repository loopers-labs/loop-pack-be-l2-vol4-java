package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final String DEFAULT_USERID   = "user1";
    private final String DEFAULT_PASSWORD = "dlaxodid1!";
    private final String DEFAULT_NAME     = "홍길동";
    private final String DEFAULT_BIRTHDAY = "1990-01-01";
    private final String DEFAULT_EMAIL    = "test@test.com";

    private final String NEW_PASSWORD     = "dlaxodid2!";

    private static final String BLANK      = "";
    private static final String SPACE      = " ";

    private UserModel createDefaultUser() {
        return new UserModel(DEFAULT_USERID, passwordEncoder.encode(DEFAULT_PASSWORD), DEFAULT_NAME, DEFAULT_BIRTHDAY, DEFAULT_EMAIL);
    }

    @DisplayName("회원 모델을 생성 할 때,")
    @Nested
    class Create {
        @DisplayName("아이디가 영문 대소문자 특수문자를 포함하지 않으면,")
        @Nested
        class UserIdFormatValidation {
            @ParameterizedTest(name = "[{index}] {0}")
            @ValueSource(strings = {
                    BLANK,
                    SPACE,
                    "유저1",     // 한글 포함
                    "user_1",   // 언더스코어 포함
                    "user-1",   // 하이픈 포함
                    "user!1",   // 특수문자 포함
            })
            @DisplayName("BAD_REQUEST 예외가 발생한다.")
            void throwsBadRequest_whenUseridIsBlank(String invalidUserId) {
                // act
                CoreException result = assertThrows(CoreException.class, () ->
                        new UserModel(invalidUserId, passwordEncoder.encode(DEFAULT_PASSWORD), DEFAULT_NAME, DEFAULT_BIRTHDAY, DEFAULT_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @DisplayName("이름이 공백이거나 2자 미만이면,")
        @Nested
        class NameFormatValidation {
            @ParameterizedTest(name = "[{index}] {0}")
            @ValueSource(strings = {BLANK, SPACE, "양"})
            @DisplayName("BAD_REQUEST 예외가 발생한다.")
            void throwsBadRequest_whenNameIsInvalid(String invalidName) {
                // act
                CoreException result = assertThrows(CoreException.class, () ->
                        new UserModel(DEFAULT_USERID, passwordEncoder.encode(DEFAULT_PASSWORD), invalidName, DEFAULT_BIRTHDAY, DEFAULT_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @DisplayName("비밀번호가 8~16자의 영문 대소문자, 숫자, 특수문자를 포함하지 않으면,")
        @Nested
        class PasswordFormatValidation {
            @ParameterizedTest(name = "[{index}] {0}")
            @ValueSource(strings = {
                    "abc1",                    // 8자 미만
                    "toolongpassword12345!",   // 16자 초과
                    "한글포함패스워드",           // 허용 문자 외
            })
            @DisplayName("BAD_REQUEST 예외가 발생한다.")
            void throwsBadRequest_whenPasswordFormatIsInvalid(String invalidPassword) {
                // act
                CoreException result = assertThrows(CoreException.class, () ->
                        UserModel.validatePassword(invalidPassword, DEFAULT_BIRTHDAY)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @Test
            @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
            void throwsBadRequest_whenPasswordContainsBirthday() {
                // act
                CoreException result = assertThrows(CoreException.class, () ->
                        UserModel.validatePassword("19900101abc!", DEFAULT_BIRTHDAY)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @DisplayName("생년월일 형식이 yyyy-mm-dd가 아니면,")
        @Nested
        class BirthDayFormatValidation {
            @ParameterizedTest(name = "[{index}] {0}")
            @ValueSource(strings = {
                    "1998-04",                   // 형식 안맞음
                    "19980410",               // 형식 안맞음
            })
            @DisplayName("BAD_REQUEST 예외가 발생한다.")
            void throwsBadRequest_whenBirthDayFormatIsInvalid(String invalidBirthDay) {
                // act
                CoreException result = assertThrows(CoreException.class, () ->
                        new UserModel(DEFAULT_USERID, passwordEncoder.encode(DEFAULT_PASSWORD), DEFAULT_NAME, invalidBirthDay, DEFAULT_EMAIL)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @DisplayName("이메일 형식이 올바르지 않으면,")
        @Nested
        class EmailFormatValidation {
            @ParameterizedTest(name = "[{index}] {0}")
            @ValueSource(strings = {
                    "test",                   // 형식 안맞음
                    "test1234",               // 형식 안맞음
            })
            @DisplayName("BAD_REQUEST 예외가 발생한다.")
            void throwsBadRequest_whenEmailFormatIsInvalid(String invalidEmail) {
                // act
                CoreException result = assertThrows(CoreException.class, () ->
                        new UserModel(DEFAULT_USERID, passwordEncoder.encode(DEFAULT_PASSWORD), DEFAULT_NAME, DEFAULT_BIRTHDAY, invalidEmail)
                );

                // assert
                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @Test
        @DisplayName("유효한 입력이면, 회원 모델이 생성된다.")
        void createsUser_whenInputsAreValid() {
            UserModel user = createDefaultUser();

            assertAll(
                    () -> assertThat(user.getUserid()).isEqualTo(DEFAULT_USERID),
                    () -> assertThat(user.getName()).isEqualTo(DEFAULT_NAME),
                    () -> assertThat(user.getEmail()).isEqualTo(DEFAULT_EMAIL),
                    () -> assertThat(user.getBirthDay()).isEqualTo(DEFAULT_BIRTHDAY),
                    () ->  assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, user.getPassword())).isTrue()
            );
        }
    }

    @DisplayName("패스워드를 변경 할 때,")
    @Nested
    class ChangePassword {

        @Test
        @DisplayName("새 인코딩된 비밀번호로 변경되면, 비밀번호가 업데이트된다.")
        void changesPassword_whenEncodedPasswordIsGiven() {
            // arrange
            UserModel user = createDefaultUser();
            String newEncoded = passwordEncoder.encode(NEW_PASSWORD);

            // act
            user.changePassword(newEncoded);

            // assert
            assertThat(user.getPassword()).isEqualTo(newEncoded);
        }
    }
}
