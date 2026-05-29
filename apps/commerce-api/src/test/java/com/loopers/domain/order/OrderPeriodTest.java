package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderPeriodTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @DisplayName("시작일과 종료일이 모두 주어지고 시작일이 종료일보다 늦지 않으면, 정상 생성된다.")
    @Test
    void createsPeriod_whenFromIsNotAfterTo() {
        // given
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 31);

        // when
        OrderPeriod period = OrderPeriod.of(from, to);

        // then
        assertAll(
            () -> assertThat(period.from()).isEqualTo(from),
            () -> assertThat(period.to()).isEqualTo(to)
        );
    }

    @DisplayName("시작일과 종료일이 같으면, 정상 생성된다.")
    @Test
    void createsPeriod_whenFromEqualsTo() {
        // given
        LocalDate sameDay = LocalDate.of(2026, 5, 15);

        // when
        OrderPeriod period = OrderPeriod.of(sameDay, sameDay);

        // then
        assertThat(period.from()).isEqualTo(period.to());
    }

    @DisplayName("시작일이 없으면, INVALID_ORDER_PERIOD 예외가 발생한다.")
    @Test
    void throwsInvalidOrderPeriod_whenFromIsNull() {
        // given
        LocalDate to = LocalDate.of(2026, 5, 31);

        // when
        CoreException exception = assertThrows(CoreException.class,
            () -> OrderPeriod.of(null, to));

        // then
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_ORDER_PERIOD);
    }

    @DisplayName("종료일이 없으면, INVALID_ORDER_PERIOD 예외가 발생한다.")
    @Test
    void throwsInvalidOrderPeriod_whenToIsNull() {
        // given
        LocalDate from = LocalDate.of(2026, 5, 1);

        // when
        CoreException exception = assertThrows(CoreException.class,
            () -> OrderPeriod.of(from, null));

        // then
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_ORDER_PERIOD);
    }

    @DisplayName("시작일이 종료일보다 늦으면, INVALID_ORDER_PERIOD 예외가 발생한다.")
    @Test
    void throwsInvalidOrderPeriod_whenFromIsAfterTo() {
        // given
        LocalDate from = LocalDate.of(2026, 5, 31);
        LocalDate to = LocalDate.of(2026, 5, 1);

        // when
        CoreException exception = assertThrows(CoreException.class,
            () -> OrderPeriod.of(from, to));

        // then
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_ORDER_PERIOD);
    }

    @DisplayName("fromAtStartOfDay 는, 시작일의 해당 존 자정을 반환한다.")
    @Test
    void fromAtStartOfDay_returnsStartOfFromDate() {
        // given
        OrderPeriod period = OrderPeriod.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        // when
        ZonedDateTime result = period.fromAtStartOfDay(KST);

        // then
        assertThat(result).isEqualTo(LocalDate.of(2026, 5, 1).atStartOfDay(KST));
    }

    @DisplayName("toExclusive 는, 종료일 다음 날의 해당 존 자정을 반환한다 (반개구간 상한).")
    @Test
    void toExclusive_returnsStartOfDayAfterToDate() {
        // given
        OrderPeriod period = OrderPeriod.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        // when
        ZonedDateTime result = period.toExclusive(KST);

        // then
        assertThat(result).isEqualTo(LocalDate.of(2026, 6, 1).atStartOfDay(KST));
    }
}
