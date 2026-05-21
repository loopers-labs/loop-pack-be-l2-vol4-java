package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginIdTest {

    @DisplayName("LoginId 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("영문 소문자와 숫자로 이루어진 10자 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenValueIsLowerAlphaNumeric() {
            // given
            String value = "loopers123";

            // when
            LoginId loginId = LoginId.of(value);

            // then
            assertThat(loginId.getValue()).isEqualTo(value);
        }

        @DisplayName("최소 길이(4자)의 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenValueIsMinLength() {
            // given
            String value = "ab12"; // 4자

            // when
            LoginId loginId = LoginId.of(value);

            // then
            assertThat(loginId.getValue()).isEqualTo(value);
        }

        @DisplayName("최대 길이(20자)의 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenValueIsMaxLength() {
            // given
            String value = "abcdefghij1234567890"; // 20자

            // when
            LoginId loginId = LoginId.of(value);

            // then
            assertThat(loginId.getValue()).isEqualTo(value);
        }

        @DisplayName("영문 소문자만으로 이루어진 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenValueContainsOnlyLowerLetters() {
            // given
            String value = "loopers";

            // when
            LoginId loginId = LoginId.of(value);

            // then
            assertThat(loginId.getValue()).isEqualTo(value);
        }

        @DisplayName("값이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class, () -> LoginId.of(null));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("로그인 ID는 비어있을 수 없습니다.")
            );
        }

        @DisplayName("값이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsEmpty() {
            // given
            String value = "";

            // when
            CoreException result = assertThrows(CoreException.class, () -> LoginId.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("로그인 ID는 비어있을 수 없습니다.")
            );
        }

        @DisplayName("값이 공백 문자로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsSpacesOnly() {
            // given
            String value = "    ";

            // when
            CoreException result = assertThrows(CoreException.class, () -> LoginId.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("로그인 ID는 비어있을 수 없습니다.")
            );
        }

        @DisplayName("값의 길이가 최소 길이(4자)보다 1자 짧으면(3자), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLengthIsOneShorterThanMinimum() {
            // given
            String value = "ab1"; // 3자

            // when
            CoreException result = assertThrows(CoreException.class, () -> LoginId.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("로그인 ID는 영문 소문자와 숫자 4~20자여야 합니다.")
            );
        }

        @DisplayName("값의 길이가 최대 길이(20자)보다 1자 길면(21자), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLengthIsOneLongerThanMaximum() {
            // given
            String value = "abcdefghij12345678901"; // 21자

            // when
            CoreException result = assertThrows(CoreException.class, () -> LoginId.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("로그인 ID는 영문 소문자와 숫자 4~20자여야 합니다.")
            );
        }

        @DisplayName("값에 영문 대문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsUpperLetters() {
            // given
            String value = "Loopers01";

            // when
            CoreException result = assertThrows(CoreException.class, () -> LoginId.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("로그인 ID는 영문 소문자와 숫자 4~20자여야 합니다.")
            );
        }

        @DisplayName("값에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsSpecialCharacters() {
            // given
            String value = "loopers!";

            // when
            CoreException result = assertThrows(CoreException.class, () -> LoginId.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("로그인 ID는 영문 소문자와 숫자 4~20자여야 합니다.")
            );
        }

        @DisplayName("값에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsKoreanChars() {
            // given
            String value = "루퍼스01";

            // when
            CoreException result = assertThrows(CoreException.class, () -> LoginId.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("로그인 ID는 영문 소문자와 숫자 4~20자여야 합니다.")
            );
        }
    }

    @DisplayName("동등성을 비교할 때, ")
    @Nested
    class Equality {

        @DisplayName("같은 값을 가진 두 LoginId 는 equals/hashCode 가 동일하다.")
        @Test
        void returnsEqual_whenValuesAreSame() {
            // given
            LoginId loginId1 = LoginId.of("loopers01");
            LoginId loginId2 = LoginId.of("loopers01");

            // when & then
            assertAll(
                () -> assertThat(loginId1).isEqualTo(loginId2),
                () -> assertThat(loginId1.hashCode()).isEqualTo(loginId2.hashCode())
            );
        }

        @DisplayName("다른 값을 가진 두 LoginId 는 equals 가 false 를 반환한다.")
        @Test
        void returnsNotEqual_whenValuesAreDifferent() {
            // given
            LoginId loginId1 = LoginId.of("loopers01");
            LoginId loginId2 = LoginId.of("loopers02");

            // when & then
            assertThat(loginId1).isNotEqualTo(loginId2);
        }
    }
}
