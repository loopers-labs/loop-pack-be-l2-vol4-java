package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class ExpiredAtTest {

    @DisplayName("만료 시각을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("현재 시각 이후면 입력값을 그대로 보존한 만료 시각이 생성된다.")
        @Test
        void createsExpiredAt_whenValueIsFuture() {
            // arrange
            ZonedDateTime value = ZonedDateTime.now().plusDays(7);

            // act
            ExpiredAt expiredAt = ExpiredAt.from(value);

            // assert
            assertThat(expiredAt.value()).isEqualTo(value);
        }

        @DisplayName("현재 시각 이전이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsPast() {
            // arrange
            ZonedDateTime value = ZonedDateTime.now().minusDays(1);

            // act & assert
            assertThatThrownBy(() -> ExpiredAt.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> ExpiredAt.from(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
