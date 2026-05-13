package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
public class LoginIdTest {

    @Nested
    @DisplayName("LoginId 검증")
    class LoginIdValidation {

        @DisplayName("아이디가 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t", "\n"})
        void given_nullOrBlankLoginId_when_createLoginId_then_throwsBadRequestException(String invalidId) {
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new LoginId(invalidId)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("아이디가 4자 미만이거나 20자를 초과하면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {
                "abc",                       // 3자 (최소 미만)
                "abcdefghij1234567890a"      // 21자 (최대 초과)
        })
        void given_loginIdLengthOutOfRange_when_createLoginId_then_throwsBadRequestException(String invalidId) {
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new LoginId(invalidId)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("아이디에 허용되지 않은 문자가 포함되면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {
                "User1234",            // 영문 대문자
                "user1234!",           // 허용 외 특수문자(!)
                "user 1234",           // 공백
                "user@1234",           // @
                "user한글",            // 한글
                "user/1234",           // 경로 문자 (/)
                "user\\1234",          // 경로 문자 (\)
                "user..test"           // 연속된 점
        })
        void given_loginIdWithInvalidCharacters_when_createLoginId_then_throwsBadRequestException(String invalidId) {
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new LoginId(invalidId)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("아이디가 숫자나 특수문자로 시작하면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {
                "1user",               // 숫자로 시작
                "_user",               // 특수문자로 시작
                "-user"                // 특수문자로 시작
        })
        void given_loginIdStartingWithNumberOrSpecial_when_createLoginId_then_throwsBadRequestException(String invalidId) {
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new LoginId(invalidId)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("아이디가 특수문자로 끝나면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {
                "user_",
                "user-"
        })
        void given_loginIdEndingWithSpecial_when_createLoginId_then_throwsBadRequestException(String invalidId) {
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new LoginId(invalidId)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("아이디에 특수문자가 연속되면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(strings = {
                "user__name",
                "user--name",
                "user-_name"
        })
        void given_loginIdWithConsecutiveSpecials_when_createLoginId_then_throwsBadRequestException(String invalidId) {
            CoreException result = assertThrows(
                    CoreException.class,
                    () -> new LoginId(invalidId)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        // 해피케이스
        @DisplayName("아이디가 영문으로 시작하고 영문/숫자/특수문자(_, -)로 구성되고 4~20자이면 정상 생성된다")
        @ParameterizedTest
        @ValueSource(strings = {
                "abcd",                          // 4자 (최소 경계)
                "abcdefghij1234567890",          // 20자 (최대 경계)
                "user1234",                      // 영문 + 숫자
                "user_name",                     // 언더스코어 (중간)
                "user-name",                     // 하이픈 (중간)
                "u_ser-name123",                 // 영문/숫자/특수문자 혼합
                "abcd1"                          // 영문 시작 + 숫자
        })
        void given_validLoginId_when_createLoginId_then_createsLoginId(String validId) {
            // Act
            LoginId loginId = new LoginId(validId);

            // Assert
            assertThat(loginId).isNotNull();
        }
    }
}
