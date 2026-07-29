package com.loopers.batch.job.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * targetDate 가 없으면 즉시 실패해야 한다.
 * 없는 채로 통과시키면 JobParameters 가 비어 완료 가드(JobInstanceAlreadyCompleteException)가 적용되지 않고,
 * 두 번째 실행부터 같은 JobInstance 를 재사용해 완료된 Step 을 모두 스킵한다 — 조용히 아무것도 하지 않는다.
 */
class TargetDateValidatorTest {

    private final TargetDateValidator validator = new TargetDateValidator();

    private JobParameters params(String targetDate) {
        return new JobParametersBuilder().addString("targetDate", targetDate).toJobParameters();
    }

    @Test
    @DisplayName("targetDate 가 없으면 거부한다")
    void givenNoTargetDate_whenValidate_thenRejected() {
        assertThatThrownBy(() -> validator.validate(new JobParameters()))
                .isInstanceOf(JobParametersInvalidException.class)
                .hasMessageContaining("targetDate");
    }

    @Test
    @DisplayName("JobParameters 자체가 null 이어도 거부한다")
    void givenNullParameters_whenValidate_thenRejected() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(JobParametersInvalidException.class);
    }

    @Test
    @DisplayName("yyyyMMdd 가 아니면 거부한다")
    void givenWrongFormat_whenValidate_thenRejected() {
        assertThatThrownBy(() -> validator.validate(params("2026-07-26")))
                .isInstanceOf(JobParametersInvalidException.class);
    }

    @Test
    @DisplayName("달력에 없는 날짜는 거부한다")
    void givenNonExistentDate_whenValidate_thenRejected() {
        assertThatThrownBy(() -> validator.validate(params("20260231")))
                .isInstanceOf(JobParametersInvalidException.class);
    }

    @Test
    @DisplayName("yyyyMMdd 면 통과한다")
    void givenValidTargetDate_whenValidate_thenPasses() {
        assertThatCode(() -> validator.validate(params("20260726"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("재집계용 rerun 이 붙어도 통과한다")
    void givenRerunParameter_whenValidate_thenPasses() {
        JobParameters withRerun = new JobParametersBuilder()
                .addString("targetDate", "20260726")
                .addLong("rerun", 1L)
                .toJobParameters();

        assertThatCode(() -> validator.validate(withRerun)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("검증을 통과한 값은 LocalDate 로 읽어 쓴다")
    void givenValidTargetDate_whenParsed_thenLocalDate() {
        assertThat(TargetDateValidator.parse(params("20260726"))).isEqualTo(LocalDate.of(2026, 7, 26));
    }
}
