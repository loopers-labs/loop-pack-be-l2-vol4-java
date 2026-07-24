package com.loopers.batch.job.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class RankingBatchJobParametersValidatorTest {

    private final RankingBatchJobParametersValidator validator = new RankingBatchJobParametersValidator();

    @DisplayName("validate()를 실행할 때,")
    @Nested
    class Validate {

        @DisplayName("period/periodKey가 올바르면 예외 없이 통과한다.")
        @Test
        void passes_whenParametersAreValid() {
            JobParameters parameters = new JobParametersBuilder()
                .addString("period", "WEEKLY")
                .addString("periodKey", "2026W29")
                .toJobParameters();

            assertThatCode(() -> validator.validate(parameters)).doesNotThrowAnyException();
        }

        @DisplayName("period/periodKey가 잘못되면 JobParametersInvalidException을 던진다.")
        @Test
        void throwsJobParametersInvalidException_whenParametersAreInvalid() {
            JobParameters parameters = new JobParametersBuilder()
                .addString("period", "DAILY")
                .addString("periodKey", "2026W29")
                .toJobParameters();

            assertThatExceptionOfType(JobParametersInvalidException.class)
                .isThrownBy(() -> validator.validate(parameters));
        }

        @DisplayName("periodKey가 누락되면 JobParametersInvalidException을 던진다.")
        @Test
        void throwsJobParametersInvalidException_whenPeriodKeyIsMissing() {
            JobParameters parameters = new JobParametersBuilder()
                .addString("period", "WEEKLY")
                .toJobParameters();

            assertThatExceptionOfType(JobParametersInvalidException.class)
                .isThrownBy(() -> validator.validate(parameters));
        }
    }
}
