package com.loopers.batch.job.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductRankingCleanupJobParametersTest {

    @DisplayName("상품 랭킹 Cleanup Job Parameter를 해석할 때")
    @Nested
    class Parse {

        @DisplayName("양수 Long targetSnapshotId를 Cleanup 대상으로 해석한다.")
        @Test
        void parsesTargetSnapshotId() {
            // arrange
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("targetSnapshotId", 120L)
                .toJobParameters();

            // act
            ProductRankingCleanupJobParameters result =
                ProductRankingCleanupJobParameters.from(jobParameters);

            // assert
            assertThat(result.targetSnapshotId()).isEqualTo(120L);
        }

        @DisplayName("targetSnapshotId가 양수가 아니면 거절한다.")
        @ValueSource(longs = {0L, -1L})
        @ParameterizedTest
        void rejectsNonPositiveTargetSnapshotId(long targetSnapshotId) {
            // arrange
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("targetSnapshotId", targetSnapshotId)
                .toJobParameters();

            // act & assert
            assertThatThrownBy(
                () -> ProductRankingCleanupJobParameters.from(jobParameters)
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetSnapshotId");
        }

        @DisplayName("targetSnapshotId가 Long이 아니면 거절한다.")
        @Test
        void rejectsStringTargetSnapshotId() {
            // arrange
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetSnapshotId", "120")
                .toJobParameters();

            // act & assert
            assertThatThrownBy(
                () -> ProductRankingCleanupJobParameters.from(jobParameters)
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetSnapshotId");
        }

        @DisplayName("targetSnapshotId가 누락되면 거절한다.")
        @Test
        void rejectsMissingTargetSnapshotId() {
            // act & assert
            assertThatThrownBy(
                () -> ProductRankingCleanupJobParameters.from(new JobParameters())
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetSnapshotId");
        }

        @DisplayName("targetSnapshotId가 identifying Parameter가 아니면 거절한다.")
        @Test
        void rejectsNonIdentifyingTargetSnapshotId() {
            // arrange
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("targetSnapshotId", 120L, false)
                .toJobParameters();

            // act & assert
            assertThatThrownBy(
                () -> ProductRankingCleanupJobParameters.from(jobParameters)
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifying");
        }

        @DisplayName("run.id 같은 추가 identifying Parameter를 거절한다.")
        @Test
        void rejectsAdditionalIdentifyingParameter() {
            // arrange
            JobParameters jobParameters = new JobParametersBuilder()
                .addLong("targetSnapshotId", 120L)
                .addLong("run.id", 1L)
                .toJobParameters();

            // act & assert
            assertThatThrownBy(
                () -> ProductRankingCleanupJobParameters.from(jobParameters)
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifying");
        }
    }
}
