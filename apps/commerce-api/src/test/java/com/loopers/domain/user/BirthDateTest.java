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

        @DisplayName("null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> new BirthDate(null));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("미래 일자이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsFuture() {
            // arrange
            LocalDate future = LocalDate.now().plusDays(1);

            // act
            CoreException result = assertThrows(CoreException.class, () -> new BirthDate(future));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("과거 또는 오늘 일자는 정상적으로 생성된다.")
        @Test
        void createsBirthDate_whenValueIsPastOrToday() {
            // arrange
            LocalDate past = LocalDate.of(1999, 1, 1);
            LocalDate today = LocalDate.now();

            // act
            BirthDate pastBirthDate = new BirthDate(past);
            BirthDate todayBirthDate = new BirthDate(today);

            // assert
            assertAll(
                () -> assertThat(pastBirthDate.getValue()).isEqualTo(past),
                () -> assertThat(todayBirthDate.getValue()).isEqualTo(today)
            );
        }
    }
}
