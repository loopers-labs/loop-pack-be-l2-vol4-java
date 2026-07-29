package com.loopers.batch.job.ranking;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * targetDate 를 필수로 강제한다. 기본값을 두지 않는 이유가 있다 —
 * 값이 없으면 JobParameters 가 비고, SimpleJobRepository 의 완료 가드가 식별 파라미터가 빈 경우에는
 * 적용되지 않아 두 번째 실행부터 같은 JobInstance 를 재사용한다.
 * 그러면 이전 실행에서 COMPLETED 된 Step 이 전부 스킵돼 실패도 로그도 없이 아무것도 하지 않는다.
 *
 * <p>실행 시 {@code --} 를 붙이면(예: {@code --targetDate=20260726}) 값이 프로퍼티로 새어
 * JobParameters 에는 들어오지 않으므로 여기서 걸린다.
 */
public class TargetDateValidator implements JobParametersValidator {

    public static final String KEY = "targetDate";

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    @Override
    public void validate(@Nullable JobParameters parameters) throws JobParametersInvalidException {
        String raw = parameters == null ? null : parameters.getString(KEY);
        if (raw == null || raw.isBlank()) {
            throw new JobParametersInvalidException(
                    "targetDate 는 필수다. 예: targetDate=20260726 (-- 를 붙이면 프로퍼티로 새어 전달되지 않는다)");
        }
        try {
            LocalDate.parse(raw, FORMAT);
        } catch (DateTimeParseException e) {
            throw new JobParametersInvalidException("targetDate 는 yyyyMMdd 형식의 실재하는 날짜여야 한다: " + raw);
        }
    }

    /** 검증을 통과한 파라미터에서 날짜를 읽는다. */
    public static LocalDate parse(JobParameters parameters) {
        return parse(parameters.getString(KEY));
    }

    public static LocalDate parse(String raw) {
        return LocalDate.parse(raw, FORMAT);
    }
}
