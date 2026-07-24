package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingDateRange;
import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductRankingJobParameterValidatorTest {

    private final ProductRankingJobParameterValidator validator = new ProductRankingJobParameterValidator();

    @Test
    @DisplayName("Job Parameter에서 period, startDate, endDate를 읽어 기간을 검증한다.")
    void validate_ShouldParseAndValidateJobParameters() {
        JobParameters jobParameters = new JobParametersBuilder()
            .addString("period", "WEEKLY")
            .addString("startDate", "20260720")
            .addString("endDate", "20260726")
            .toJobParameters();

        RankingDateRange dateRange = validator.validateRange(jobParameters);

        assertThat(dateRange.period()).isEqualTo(RankingPeriod.WEEKLY);
        assertThat(dateRange.startDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(dateRange.endDate()).isEqualTo(LocalDate.of(2026, 7, 26));
    }

    @Test
    @DisplayName("필수 Job Parameter가 없으면 실패한다.")
    void validate_WhenRequiredParameterMissing_ShouldThrowException() {
        JobParameters jobParameters = new JobParametersBuilder()
            .addString("period", "WEEKLY")
            .addString("startDate", "20260720")
            .toJobParameters();

        assertThatThrownBy(() -> validator.validateRange(jobParameters))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("지원하지 않는 period가 전달되면 실패한다.")
    void validate_WhenPeriodInvalid_ShouldThrowException() {
        JobParameters jobParameters = new JobParametersBuilder()
            .addString("period", "CUSTOM")
            .addString("startDate", "20260720")
            .addString("endDate", "20260726")
            .toJobParameters();

        assertThatThrownBy(() -> validator.validateRange(jobParameters))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
