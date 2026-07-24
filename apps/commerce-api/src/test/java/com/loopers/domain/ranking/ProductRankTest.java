package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductRankTest {

    @DisplayName("생성할 때,")
    @Nested
    class Create {

        @DisplayName("value가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNegative() {
            // when
            CoreException result = assertThrows(CoreException.class, () -> new ProductRank(-1L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("표시용 순위를 계산할 때,")
    @Nested
    class Position {

        @DisplayName("0-based value에 1을 더한 1-based 순위를 반환한다.")
        @Test
        void returnsOneBasedPosition() {
            // given
            ProductRank rank = new ProductRank(0L);

            // when
            long result = rank.position();

            // then
            assertThat(result).isEqualTo(1L);
        }
    }
}
