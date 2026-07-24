package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingMvQueryConditionTest {

    @DisplayName("생성할 때,")
    @Nested
    class Create {

        @DisplayName("period 가 DAILY 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPeriodIsDaily() {
            CoreException result = assertThrows(CoreException.class,
                () -> new RankingMvQueryCondition(RankingPeriod.DAILY, LocalDate.now(), 1, 20));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("date 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDateIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new RankingMvQueryCondition(RankingPeriod.WEEKLY, null, 1, 20));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("page 가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPageIsLessThanOne() {
            CoreException result = assertThrows(CoreException.class,
                () -> new RankingMvQueryCondition(RankingPeriod.WEEKLY, LocalDate.now(), 0, 20));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("size 가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenSizeIsLessThanOne() {
            CoreException result = assertThrows(CoreException.class,
                () -> new RankingMvQueryCondition(RankingPeriod.MONTHLY, LocalDate.now(), 1, 0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("집계 대표일을 계산할 때,")
    @Nested
    class AggregateDate {

        @DisplayName("WEEKLY 는 date 가 속한 주의 월요일을 반환한다.")
        @Test
        void returnsMondayOfWeek_whenWeekly() {
            RankingMvQueryCondition condition =
                new RankingMvQueryCondition(RankingPeriod.WEEKLY, LocalDate.of(2024, 1, 3), 1, 20);
            assertThat(condition.aggregateDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        }

        @DisplayName("MONTHLY 는 date 가 속한 달의 1일을 반환한다.")
        @Test
        void returnsFirstDayOfMonth_whenMonthly() {
            RankingMvQueryCondition condition =
                new RankingMvQueryCondition(RankingPeriod.MONTHLY, LocalDate.of(2024, 1, 15), 1, 20);
            assertThat(condition.aggregateDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        }
    }
}
