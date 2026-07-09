package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaitingQueueRankTest {

    @DisplayName("순번 값 객체를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("value가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNegative() {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new WaitingQueueRank(-1L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("1-based 순번을 조회할 때,")
    @Nested
    class Position {

        @DisplayName("0-based value에 1을 더한 값을 반환한다.")
        @Test
        void returnsValuePlusOne() {
            // given
            WaitingQueueRank rank = new WaitingQueueRank(0L);

            // when
            long position = rank.position();

            // then
            assertThat(position).isEqualTo(1L);
        }
    }
}
