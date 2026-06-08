package com.loopers.common.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("of 로 생성하면 value 를 보관한다")
    void givenValue_whenOf_thenHoldsValue() {
        assertThat(Money.of(29_000L).value()).isEqualTo(29_000L);
    }

    @Test
    @DisplayName("음수 금액이면 BAD_REQUEST 예외가 발생한다")
    void givenNegative_whenOf_thenThrowsBadRequest() {
        assertThatThrownBy(() -> Money.of(-1L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("plus 는 두 금액을 더한다")
    void givenTwoMoney_whenPlus_thenSums() {
        assertThat(Money.of(29_000L).plus(Money.of(15_000L))).isEqualTo(Money.of(44_000L));
    }

    @Test
    @DisplayName("times 는 수량만큼 곱한다")
    void givenQuantity_whenTimes_thenMultiplies() {
        assertThat(Money.of(29_000L).times(3)).isEqualTo(Money.of(87_000L));
    }

    @Test
    @DisplayName("같은 value 면 동등하다 (값 객체)")
    void givenSameValue_whenEquals_thenEqual() {
        assertThat(Money.of(1_000L)).isEqualTo(Money.of(1_000L));
    }

    @Test
    @DisplayName("ZERO 는 0 원이다")
    void givenZero_thenValueIsZero() {
        assertThat(Money.ZERO.value()).isEqualTo(0L);
    }
}
