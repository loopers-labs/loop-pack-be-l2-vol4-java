package com.loopers.batch.job.productranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.Set;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;

public class ProductRankingJobParametersValidator implements JobParametersValidator {

  public static final String TARGET_DATE = "targetDate";
  public static final String REBUILD_SEQUENCE = "rebuildSequence";

  private static final DateTimeFormatter TARGET_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
  private static final Set<String> ALLOWED_PARAMETERS = Set.of(TARGET_DATE, REBUILD_SEQUENCE);

  @Override
  public void validate(JobParameters parameters) throws JobParametersInvalidException {
    Set<String> unknownParameters = new HashSet<>(parameters.getParameters().keySet());
    unknownParameters.removeAll(ALLOWED_PARAMETERS);
    if (!unknownParameters.isEmpty()) {
      throw new JobParametersInvalidException("지원하지 않는 Job 파라미터입니다: " + unknownParameters);
    }

    JobParameter<?> targetDateParameter = parameters.getParameter(TARGET_DATE);
    if (targetDateParameter == null || !targetDateParameter.isIdentifying()) {
      throw new JobParametersInvalidException("targetDate는 필수 식별 파라미터입니다.");
    }
    if (!(targetDateParameter.getValue() instanceof String targetDate)) {
      throw new JobParametersInvalidException("targetDate는 yyyyMMdd 문자열이어야 합니다.");
    }
    parseTargetDate(targetDate);

    JobParameter<?> rebuildParameter = parameters.getParameter(REBUILD_SEQUENCE);
    if (rebuildParameter != null) {
      validateRebuildSequence(rebuildParameter);
    }
  }

  public static LocalDate parseTargetDate(String targetDate) throws JobParametersInvalidException {
    if (targetDate == null) {
      throw new JobParametersInvalidException("targetDate는 필수입니다.");
    }

    try {
      return LocalDate.parse(targetDate, TARGET_DATE_FORMATTER);
    } catch (DateTimeParseException exception) {
      throw new JobParametersInvalidException("targetDate는 유효한 yyyyMMdd 날짜여야 합니다.");
    }
  }

  private void validateRebuildSequence(JobParameter<?> rebuildParameter)
      throws JobParametersInvalidException {
    if (!rebuildParameter.isIdentifying()) {
      throw new JobParametersInvalidException("rebuildSequence는 식별 파라미터여야 합니다.");
    }

    Object value = rebuildParameter.getValue();
    if (!(value instanceof String sequence) || !sequence.matches("[1-9][0-9]*")) {
      throw new JobParametersInvalidException("rebuildSequence는 1 이상의 정수여야 합니다.");
    }
  }
}
