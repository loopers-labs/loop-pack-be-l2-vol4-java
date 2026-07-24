package com.loopers.batch.job.ranking;

import com.loopers.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductRankingSnapshotJobParametersTest {

    @DisplayName("상품 랭킹 Snapshot Job Parameter를 해석할 때")
    @Nested
    class Parse {

        @DisplayName("기간, 집계 종료일과 Long revision을 변환하고 기간 시작일을 계산한다.")
        @CsvSource({
            "WEEKLY, 2026-07-13",
            "MONTHLY, 2026-07-01"
        })
        @ParameterizedTest
        void parsesParametersAndCalculatesPeriodStart(
            String period,
            LocalDate expectedPeriodStart
        ) {
            // arrange
            JobParameters jobParameters = validJobParameters(period);

            // act
            ProductRankingSnapshotJobParameters result =
                ProductRankingSnapshotJobParameters.from(jobParameters);

            // assert
            assertAll(
                () -> assertThat(result.period()).isEqualTo(RankingPeriod.valueOf(period)),
                () -> assertThat(result.aggregationEndDate()).isEqualTo(LocalDate.of(2026, 7, 19)),
                () -> assertThat(result.revision()).isEqualTo(1),
                () -> assertThat(result.periodStart()).isEqualTo(expectedPeriodStart)
            );
        }

        @DisplayName("WEEKLY와 MONTHLY가 아닌 기간은 거절한다.")
        @ValueSource(strings = {"DAILY", "daily", "YEARLY", ""})
        @ParameterizedTest
        void rejectsUnsupportedPeriod(String period) {
            // arrange
            JobParameters jobParameters = validJobParameters(period);

            // act & assert
            assertThatThrownBy(() -> ProductRankingSnapshotJobParameters.from(jobParameters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period");
        }

        @DisplayName("집계 종료일이 유효한 yyyyMMdd 날짜가 아니면 거절한다.")
        @ValueSource(strings = {"2026-07-19", "20260230", "hello"})
        @ParameterizedTest
        void rejectsInvalidAggregationEndDate(String aggregationEndDate) {
            // arrange
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("period", "WEEKLY")
                .addString("aggregationEndDate", aggregationEndDate)
                .addLong("revision", 1L)
                .toJobParameters();

            // act & assert
            assertThatThrownBy(() -> ProductRankingSnapshotJobParameters.from(jobParameters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregationEndDate");
        }

        @DisplayName("revision이 1 이상 int 범위가 아니면 거절한다.")
        @ValueSource(longs = {0L, -1L, 2_147_483_648L})
        @ParameterizedTest
        void rejectsRevisionOutsideSupportedRange(long revision) {
            // arrange
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("period", "WEEKLY")
                .addString("aggregationEndDate", "20260719")
                .addLong("revision", revision)
                .toJobParameters();

            // act & assert
            assertThatThrownBy(() -> ProductRankingSnapshotJobParameters.from(jobParameters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revision");
        }

        @DisplayName("revision을 String으로 전달하면 Long 타입 계약 위반으로 거절한다.")
        @Test
        void rejectsStringRevision() {
            // arrange
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("period", "WEEKLY")
                .addString("aggregationEndDate", "20260719")
                .addString("revision", "1")
                .toJobParameters();

            // act & assert
            assertThatThrownBy(() -> ProductRankingSnapshotJobParameters.from(jobParameters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revision");
        }

        @DisplayName("run.id 같은 추가 identifying Parameter는 거절한다.")
        @Test
        void rejectsAdditionalIdentifyingParameter() {
            // arrange
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("period", "WEEKLY")
                .addString("aggregationEndDate", "20260719")
                .addLong("revision", 1L)
                .addLong("run.id", 1L)
                .toJobParameters();

            // act & assert
            assertThatThrownBy(() -> ProductRankingSnapshotJobParameters.from(jobParameters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifying");
        }

        @DisplayName("필수 Parameter가 누락되면 거절한다.")
        @ValueSource(strings = {"period", "aggregationEndDate", "revision"})
        @ParameterizedTest
        void rejectsMissingRequiredParameter(String missingParameter) {
            // arrange
            JobParametersBuilder builder = new JobParametersBuilder();
            if (!missingParameter.equals("period")) {
                builder.addString("period", "WEEKLY");
            }
            if (!missingParameter.equals("aggregationEndDate")) {
                builder.addString("aggregationEndDate", "20260719");
            }
            if (!missingParameter.equals("revision")) {
                builder.addLong("revision", 1L);
            }

            // act & assert
            assertThatThrownBy(
                () -> ProductRankingSnapshotJobParameters.from(builder.toJobParameters())
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missingParameter);
        }
    }

    private JobParameters validJobParameters(String period) {
        return new JobParametersBuilder()
            .addString("period", period)
            .addString("aggregationEndDate", "20260719")
            .addLong("revision", 1L)
            .toJobParameters();
    }
}
