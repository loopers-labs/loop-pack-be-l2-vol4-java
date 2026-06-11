package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BirthDateTest {

    @DisplayName("BirthDate 생성 시,")
    @Nested
    class Create {

        @DisplayName("유효한 날짜면 정상 생성된다.")
        @Test
        void creates_whenValid() {
            // arrange
            LocalDate date = LocalDate.of(1990, 1, 1);

            // act
            BirthDate birthDate = new BirthDate(date);

            // assert
            assertThat(birthDate.value()).isEqualTo(date);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> new BirthDate(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
