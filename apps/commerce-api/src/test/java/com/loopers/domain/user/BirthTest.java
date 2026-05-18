package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BirthTest {

    @DisplayName("생년월일을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("오늘 이전의 날짜이면, 정상적으로 생성된다.")
        @Test
        void createsBirth_whenValueIsPastDate() {
            // arrange
            LocalDate value = LocalDate.of(1990, 1, 1);

            // act
            Birth birth = new Birth(value);

            // assert
            assertThat(birth.value()).isEqualTo(value);
        }

        @DisplayName("오늘 날짜이면, 정상적으로 생성된다.")
        @Test
        void createsBirth_whenValueIsToday() {
            // arrange
            LocalDate today = LocalDate.now();

            // act
            Birth birth = new Birth(today);

            // assert
            assertThat(birth.value()).isEqualTo(today);
        }

        @DisplayName("값이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new Birth(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("미래 날짜이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsFuture() {
            // arrange
            LocalDate future = LocalDate.now().plusDays(1);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new Birth(future));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("asCompact 는 yyyyMMdd 형식 문자열을 반환한다.")
    @Test
    void asCompact_returnsYyyyMmDd() {
        // arrange
        Birth birth = new Birth(LocalDate.of(1990, 1, 1));

        // assert
        assertThat(birth.asCompact()).isEqualTo("19900101");
    }
}
