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

class PasswordTest {

    @DisplayName("Password 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("영문 대소문자·숫자·특수문자가 모두 포함된 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenValueContainsAllCharClasses() {
            // given
            String value = "Abcd1234!";

            // when
            Password password = Password.of(value);

            // then
            assertThat(password.getValue()).isEqualTo(value);
        }

        @DisplayName("영문자만으로 이루어진 8자 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenValueContainsOnlyLetters() {
            // given
            String value = "Abcdefgh";

            // when
            Password password = Password.of(value);

            // then
            assertThat(password.getValue()).isEqualTo(value);
        }

        @DisplayName("최소 길이(8자)의 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenValueIsMinLength() {
            // given
            String value = "Abc1234!"; // 8자

            // when
            Password password = Password.of(value);

            // then
            assertThat(password.getValue()).isEqualTo(value);
        }

        @DisplayName("최대 길이(16자)의 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenValueIsMaxLength() {
            // given
            String value = "AbCdEfGh123456!@"; // 16자

            // when
            Password password = Password.of(value);

            // then
            assertThat(password.getValue()).isEqualTo(value);
        }

        @DisplayName("값이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class, () -> Password.of(null));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsEmpty() {
            // given
            String value = "";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Password.of(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값이 공백 문자로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsSpacesOnly() {
            // given
            String value = "   ";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Password.of(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값의 길이가 최소 길이(8자)보다 1자 짧으면(7자), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLengthIsOneShorterThanMinimum() {
            // given
            String value = "Abc1234"; // 7자

            // when
            CoreException result = assertThrows(CoreException.class, () -> Password.of(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값의 길이가 최대 길이(16자)보다 1자 길면(17자), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLengthIsOneLongerThanMaximum() {
            // given
            String value = "Abcd12345!@#$%^&*"; // 17자

            // when
            CoreException result = assertThrows(CoreException.class, () -> Password.of(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값에 공백 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsSpace() {
            // given
            String value = "Abcd1234 ";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Password.of(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("값에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsKoreanChars() {
            // given
            String value = "Abcd12삼사";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Password.of(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호의 생년월일 포함 정책을 검증할 때, ")
    @Nested
    class RequireNotContainingBirthDate {

        @DisplayName("비밀번호 값이 생년월일을 yyyyMMdd 형식으로 포함하면, BAD_REQUEST 예외를 던진다.")
        @Test
        void throwsBadRequest_whenValueContainsBirthDate() {
            // given
            Password password = Password.of("ab19950315!");
            BirthDate birthDate = BirthDate.of(LocalDate.of(1995, 3, 15));

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> password.requireNotContaining(birthDate));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("비밀번호에 생년월일을 포함할 수 없습니다.")
            );
        }

        @DisplayName("비밀번호 값이 생년월일을 포함하지 않으면, 예외 없이 통과한다.")
        @Test
        void passesSilently_whenValueDoesNotContainBirthDate() {
            // given
            Password password = Password.of("Abcd1234!");
            BirthDate birthDate = BirthDate.of(LocalDate.of(1995, 3, 15));

            // when & then
            password.requireNotContaining(birthDate);
        }

        @DisplayName("비밀번호 값에 생년월일 일부(MMdd)만 들어 있으면, 예외 없이 통과한다.")
        @Test
        void passesSilently_whenValueContainsOnlyPartOfBirthDate() {
            // given - 1995년 03월 15일의 일부인 "0315" 만 포함
            Password password = Password.of("Abcd0315!");
            BirthDate birthDate = BirthDate.of(LocalDate.of(1995, 3, 15));

            // when & then
            password.requireNotContaining(birthDate);
        }

        @DisplayName("생년월일이 null 이면, 예외 없이 통과한다.")
        @Test
        void passesSilently_whenBirthDateIsNull() {
            // given
            Password password = Password.of("Abcd1234!");

            // when & then
            password.requireNotContaining(null);
        }
    }

    @DisplayName("동등성을 비교할 때, ")
    @Nested
    class Equality {

        @DisplayName("같은 값을 가진 두 Password 는 equals/hashCode 가 동일하다.")
        @Test
        void returnsEqual_whenValuesAreSame() {
            // given
            Password password1 = Password.of("Abcd1234!");
            Password password2 = Password.of("Abcd1234!");

            // when & then
            assertAll(
                () -> assertThat(password1).isEqualTo(password2),
                () -> assertThat(password1.hashCode()).isEqualTo(password2.hashCode())
            );
        }

        @DisplayName("다른 값을 가진 두 Password 는 equals 가 false 를 반환한다.")
        @Test
        void returnsNotEqual_whenValuesAreDifferent() {
            // given
            Password password1 = Password.of("Abcd1234!");
            Password password2 = Password.of("Xyz!9876@");

            // when & then
            assertThat(password1).isNotEqualTo(password2);
        }
    }
}
