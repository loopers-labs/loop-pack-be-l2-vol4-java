package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingQueryConditionTest {

    @DisplayName("생성할 때,")
    @Nested
    class Create {

        @DisplayName("page가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPageIsLessThanOne() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new RankingQueryCondition(LocalDate.now(), 0, 20));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("size가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenSizeIsLessThanOne() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new RankingQueryCondition(LocalDate.now(), 1, 0));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("date가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDateIsNull() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new RankingQueryCondition(null, 1, 20));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("start/end 범위를 계산할 때,")
    @Nested
    class Range {

        @DisplayName("1페이지는 0부터 size-1까지의 범위를 갖는다.")
        @Test
        void returnsFirstPageRange() {
            // given
            RankingQueryCondition condition = new RankingQueryCondition(LocalDate.now(), 1, 20);

            // then
            assertAll(
                    () -> assertThat(condition.start()).isEqualTo(0L),
                    () -> assertThat(condition.end()).isEqualTo(19L)
            );
        }

        @DisplayName("2페이지는 size부터 이어지는 범위를 갖는다.")
        @Test
        void returnsSecondPageRange() {
            // given
            RankingQueryCondition condition = new RankingQueryCondition(LocalDate.now(), 2, 20);

            // then
            assertAll(
                    () -> assertThat(condition.start()).isEqualTo(20L),
                    () -> assertThat(condition.end()).isEqualTo(39L)
            );
        }
    }
}
