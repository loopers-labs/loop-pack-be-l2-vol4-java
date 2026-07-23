package com.loopers.batch.job.productranking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;

class ProductRankingJobParametersValidatorTest {

  private final ProductRankingJobParametersValidator validator =
      new ProductRankingJobParametersValidator();

  @DisplayName("targetDate는 yyyyMMdd 문자열 식별 파라미터여야 한다.")
  @Test
  void validatesTargetDate() {
    assertThatCode(() -> validator.validate(parameters("20260722", null)))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> validator.validate(new JobParameters()))
        .isInstanceOf(JobParametersInvalidException.class);
    assertThatThrownBy(() -> validator.validate(parameters("20260230", null)))
        .isInstanceOf(JobParametersInvalidException.class);
    assertThatThrownBy(
            () ->
                validator.validate(
                    new JobParametersBuilder()
                        .addString("targetDate", "20260722", false)
                        .toJobParameters()))
        .isInstanceOf(JobParametersInvalidException.class);
  }

  @DisplayName("rebuildSequence는 식별 가능한 문자열 양의 정수로만 허용한다.")
  @Test
  void validatesCanonicalRebuildSequence() {
    assertThatCode(() -> validator.validate(parameters("20260722", "1")))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> validator.validate(parameters("20260722", "0")))
        .isInstanceOf(JobParametersInvalidException.class);
    assertThatThrownBy(() -> validator.validate(parameters("20260722", "1.5")))
        .isInstanceOf(JobParametersInvalidException.class);
    assertThatThrownBy(
            () ->
                validator.validate(
                    new JobParametersBuilder()
                        .addString("targetDate", "20260722")
                        .addLong("rebuildSequence", 1L)
                        .toJobParameters()))
        .isInstanceOf(JobParametersInvalidException.class);
  }

  @DisplayName("run.id 같은 알 수 없는 파라미터로 새 JobInstance 생성을 우회할 수 없다.")
  @Test
  void rejectsUnknownParameters() {
    JobParameters parameters =
        new JobParametersBuilder()
            .addString("targetDate", "20260722")
            .addLong("run.id", 1L)
            .toJobParameters();

    assertThatThrownBy(() -> validator.validate(parameters))
        .isInstanceOf(JobParametersInvalidException.class);
  }

  private JobParameters parameters(String targetDate, String rebuildSequence) {
    JobParametersBuilder builder = new JobParametersBuilder().addString("targetDate", targetDate);
    if (rebuildSequence != null) {
      builder.addString("rebuildSequence", rebuildSequence);
    }
    return builder.toJobParameters();
  }
}
