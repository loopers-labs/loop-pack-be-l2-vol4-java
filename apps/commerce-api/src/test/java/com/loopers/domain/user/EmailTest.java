package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @DisplayName("Email 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("표준 형식의 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsEmail_whenValueIsStandardFormat() {
            // given
            String value = "user@example.com";

            // when
            Email email = Email.of(value);

            // then
            assertThat(email.getValue()).isEqualTo(value);
        }

        @DisplayName("로컬파트에 점/숫자/`._%+-` 가 포함된 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsEmail_whenLocalPartContainsAllowedSpecialChars() {
            // given
            String value = "user.name_01+tag-x%test@example.com";

            // when
            Email email = Email.of(value);

            // then
            assertThat(email.getValue()).isEqualTo(value);
        }

        @DisplayName("최대 길이(254자)의 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsEmail_whenLengthIsMaxLength() {
            // given - "@example.com" (12자) + 로컬파트 242자 = 총 254자
            String value = "a".repeat(242) + "@example.com";

            // when
            Email email = Email.of(value);

            // then
            assertAll(
                () -> assertThat(value.length()).isEqualTo(254),
                () -> assertThat(email.getValue()).isEqualTo(value)
            );
        }

        @DisplayName("값의 길이가 최대 길이(254자)보다 1자 길면(255자), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLengthIsOneLongerThanMaximum() {
            // given - "@example.com" (12자) + 로컬파트 243자 = 총 255자
            String value = "a".repeat(243) + "@example.com";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Email.of(value));

            // then
            assertAll(
                () -> assertThat(value.length()).isEqualTo(255),
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이메일은 254자를 초과할 수 없습니다.")
            );
        }

        @DisplayName("값이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class, () -> Email.of(null));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이메일은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("값이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsEmpty() {
            // given
            String value = "";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Email.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이메일은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("값이 공백 문자로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenValueIsSpacesOnly() {
            // given
            String value = "    ";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Email.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이메일은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("값에 `@` 가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenMissingAtSign() {
            // given
            String value = "userexample.com";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Email.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이메일은 올바른 형식이어야 합니다.")
            );
        }

        @DisplayName("로컬파트가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLocalPartIsEmpty() {
            // given
            String value = "@example.com";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Email.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이메일은 올바른 형식이어야 합니다.")
            );
        }

        @DisplayName("도메인이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDomainIsEmpty() {
            // given
            String value = "user@";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Email.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이메일은 올바른 형식이어야 합니다.")
            );
        }

        @DisplayName("도메인에 `.` 가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDomainHasNoDot() {
            // given
            String value = "user@example";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Email.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이메일은 올바른 형식이어야 합니다.")
            );
        }

        @DisplayName("값에 공백 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsSpace() {
            // given
            String value = "user @example.com";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Email.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이메일은 올바른 형식이어야 합니다.")
            );
        }

        @DisplayName("값에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsKoreanChars() {
            // given
            String value = "사용자@example.com";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Email.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이메일은 올바른 형식이어야 합니다.")
            );
        }

        @DisplayName("`@` 가 두 개 이상이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenContainsMultipleAtSigns() {
            // given
            String value = "user@@example.com";

            // when
            CoreException result = assertThrows(CoreException.class, () -> Email.of(value));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이메일은 올바른 형식이어야 합니다.")
            );
        }
    }

    @DisplayName("동등성을 비교할 때, ")
    @Nested
    class Equality {

        @DisplayName("같은 값을 가진 두 Email 은 equals/hashCode 가 동일하다.")
        @Test
        void returnsEqual_whenValuesAreSame() {
            // given
            Email email1 = Email.of("user@example.com");
            Email email2 = Email.of("user@example.com");

            // when & then
            assertAll(
                () -> assertThat(email1).isEqualTo(email2),
                () -> assertThat(email1.hashCode()).isEqualTo(email2.hashCode())
            );
        }

        @DisplayName("다른 값을 가진 두 Email 은 equals 가 false 를 반환한다.")
        @Test
        void returnsNotEqual_whenValuesAreDifferent() {
            // given
            Email email1 = Email.of("user@example.com");
            Email email2 = Email.of("other@example.com");

            // when & then
            assertThat(email1).isNotEqualTo(email2);
        }
    }
}
