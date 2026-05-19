package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserNameTest {

    @DisplayName("이름 생성 시")
    @Nested
    class Create {

        @DisplayName("한글/영문으로 구성된 1~20자면 정상 생성된다")
        @Test
        void createsUserName_whenValueIsValid() {
            // given
            String value = "홍길동";

            // when
            UserName name = new UserName(value);

            // then
            assertThat(name.getValue()).isEqualTo(value);
        }

        @DisplayName("숫자가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenValueContainsDigit() {
            // given
            String value = "홍길동1";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new UserName(value));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("특수문자가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenValueContainsSpecialCharacter() {
            // given
            String value = "홍길동!";

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new UserName(value));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("21자를 초과하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenValueIsLongerThanTwentyCharacters() {
            // given
            String value = "가".repeat(21);

            // when
            CoreException ex = assertThrows(CoreException.class, () -> new UserName(value));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // given
            // when
            CoreException ex = assertThrows(CoreException.class, () -> new UserName(null));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름 마스킹 시")
    @Nested
    class Masked {

        @DisplayName("두 글자 이상이면 마지막 글자가 *로 치환된다")
        @Test
        void masksLastCharacter_whenNameHasMultipleCharacters() {
            // given
            UserName name = new UserName("홍길동");

            // when
            String masked = name.masked();

            // then
            assertThat(masked).isEqualTo("홍길*");
        }

        @DisplayName("한 글자면 *만 반환된다")
        @Test
        void returnsAsteriskOnly_whenNameIsSingleCharacter() {
            // given
            UserName name = new UserName("김");

            // when
            String masked = name.masked();

            // then
            assertThat(masked).isEqualTo("*");
        }
    }
}
