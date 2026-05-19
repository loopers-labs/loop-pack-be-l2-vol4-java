package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class BirthDateTest {

    @DisplayName("BirthDate를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("과거 일자면 입력값을 그대로 보존한 BirthDate가 생성된다.")
        @Test
        void createsBirthDate_whenValueIsPastDate() {
            // arrange
            LocalDate pastDate = LocalDate.of(1995, 3, 21);

            // act
            BirthDate birthDate = BirthDate.from(pastDate);

            // assert
            assertThat(birthDate.value()).isEqualTo(pastDate);
        }

        @DisplayName("오늘 일자면 입력값을 그대로 보존한 BirthDate가 생성된다.")
        @Test
        void createsBirthDate_whenValueIsToday() {
            // arrange
            LocalDate today = LocalDate.now();

            // act
            BirthDate birthDate = BirthDate.from(today);

            // assert
            assertThat(birthDate.value()).isEqualTo(today);
        }

        @DisplayName("미래 일자면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsFutureDate() {
            // arrange
            LocalDate tomorrow = LocalDate.now().plusDays(1);

            // act & assert
            assertThatThrownBy(() -> BirthDate.from(tomorrow))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act & assert
            assertThatThrownBy(() -> BirthDate.from(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("BirthDate를 압축 표현으로 변환할 때,")
    @Nested
    class Format {

        @DisplayName("toCompact()는 YYYYMMDD 형식 문자열을 반환한다.")
        @Test
        void toCompact_returnsYYYYMMDD() {
            // arrange
            BirthDate birthDate = BirthDate.from(LocalDate.of(1995, 3, 21));

            // act
            String compact = birthDate.toCompact();

            // assert
            assertThat(compact).isEqualTo("19950321");
        }

        @DisplayName("toShortCompact()는 YYMMDD 형식 문자열을 반환한다.")
        @Test
        void toShortCompact_returnsYYMMDD() {
            // arrange
            BirthDate birthDate = BirthDate.from(LocalDate.of(1995, 3, 21));

            // act
            String shortCompact = birthDate.toShortCompact();

            // assert
            assertThat(shortCompact).isEqualTo("950321");
        }
    }
}
