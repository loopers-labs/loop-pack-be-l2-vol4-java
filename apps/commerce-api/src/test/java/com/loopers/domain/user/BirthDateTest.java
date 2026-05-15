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

class BirthDateTest {

    @DisplayName("BirthDate 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("과거 날짜가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsBirthDate_whenValueIsPast() {
            // given
            LocalDate value = LocalDate.of(1999, 3, 22);

            // when
            BirthDate birthDate = BirthDate.of(value);

            // then
            assertThat(birthDate.getValue()).isEqualTo(value);
        }

        @DisplayName("오늘 날짜가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsBirthDate_whenValueIsToday() {
            // given
            LocalDate today = LocalDate.now();

            // when
            BirthDate birthDate = BirthDate.of(today);

            // then
            assertThat(birthDate.getValue()).isEqualTo(today);
        }

        @DisplayName("값이 null 이면, BAD_REQUEST 예외를 던진다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class, () -> BirthDate.of(null));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("생년월일은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("미래 날짜가 주어지면, BAD_REQUEST 예외를 던진다.")
        @Test
        void throwsBadRequest_whenValueIsFuture() {
            // given
            LocalDate tomorrow = LocalDate.now().plusDays(1);

            // when
            CoreException result = assertThrows(CoreException.class, () -> BirthDate.of(tomorrow));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("생년월일은 미래일 수 없습니다.")
            );
        }
    }

    @DisplayName("formatAsYyyyMmDd 를 호출할 때, ")
    @Nested
    class FormatAsYyyyMmDd {

        @DisplayName("yyyyMMdd 형식의 문자열을 반환한다.")
        @Test
        void returnsYyyyMmDdString() {
            // given
            BirthDate birthDate = BirthDate.of(LocalDate.of(1995, 3, 15));

            // when
            String result = birthDate.formatAsYyyyMmDd();

            // then
            assertThat(result).isEqualTo("19950315");
        }
    }

    @DisplayName("동등성을 비교할 때, ")
    @Nested
    class Equality {

        @DisplayName("같은 값을 가진 두 BirthDate 는 equals/hashCode 가 동일하다.")
        @Test
        void returnsEqual_whenValuesAreSame() {
            // given
            BirthDate birthDate1 = BirthDate.of(LocalDate.of(1995, 3, 15));
            BirthDate birthDate2 = BirthDate.of(LocalDate.of(1995, 3, 15));

            // when & then
            assertAll(
                () -> assertThat(birthDate1).isEqualTo(birthDate2),
                () -> assertThat(birthDate1.hashCode()).isEqualTo(birthDate2.hashCode())
            );
        }

        @DisplayName("다른 값을 가진 두 BirthDate 는 equals 가 false 를 반환한다.")
        @Test
        void returnsNotEqual_whenValuesAreDifferent() {
            // given
            BirthDate birthDate1 = BirthDate.of(LocalDate.of(1995, 3, 15));
            BirthDate birthDate2 = BirthDate.of(LocalDate.of(1999, 3, 22));

            // when & then
            assertThat(birthDate1).isNotEqualTo(birthDate2);
        }
    }
}
