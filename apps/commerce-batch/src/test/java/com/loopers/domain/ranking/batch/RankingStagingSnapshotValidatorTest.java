package com.loopers.domain.ranking.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class RankingStagingSnapshotValidatorTest {

    private final RankingStagingSnapshotValidator validator = new RankingStagingSnapshotValidator();

    @DisplayName("validateOrThrow()를 실행할 때,")
    @Nested
    class ValidateOrThrow {

        @DisplayName("rank가 1부터 연속이고 productId 중복이 없으면 통과한다.")
        @Test
        void passes_whenRankIsConsecutiveAndNoDuplicateProduct() {
            List<RankingStagingRankRow> rows = List.of(
                new RankingStagingRankRow(1, 100L, 30.0),
                new RankingStagingRankRow(2, 200L, 20.0),
                new RankingStagingRankRow(3, 300L, 10.0)
            );

            assertThat(rows).isNotEmpty();
            validator.validateOrThrow(rows);
        }

        @DisplayName("빈 목록이면 통과한다.")
        @Test
        void passes_whenEmpty() {
            validator.validateOrThrow(List.of());
        }

        @DisplayName("rank가 1부터 시작하지 않으면 예외를 던진다.")
        @Test
        void throws_whenRankDoesNotStartFromOne() {
            List<RankingStagingRankRow> rows = List.of(
                new RankingStagingRankRow(2, 100L, 30.0),
                new RankingStagingRankRow(3, 200L, 20.0)
            );

            assertThatIllegalStateException().isThrownBy(() -> validator.validateOrThrow(rows));
        }

        @DisplayName("rank 사이에 빈 순위가 있으면 예외를 던진다.")
        @Test
        void throws_whenRankHasGap() {
            List<RankingStagingRankRow> rows = List.of(
                new RankingStagingRankRow(1, 100L, 30.0),
                new RankingStagingRankRow(3, 200L, 20.0)
            );

            assertThatIllegalStateException().isThrownBy(() -> validator.validateOrThrow(rows));
        }

        @DisplayName("같은 productId가 두 번 이상 나오면 예외를 던진다.")
        @Test
        void throws_whenProductIdIsDuplicated() {
            List<RankingStagingRankRow> rows = List.of(
                new RankingStagingRankRow(1, 100L, 30.0),
                new RankingStagingRankRow(2, 100L, 20.0)
            );

            assertThatIllegalStateException().isThrownBy(() -> validator.validateOrThrow(rows));
        }
    }
}
