package com.loopers.batch.job.ranking;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ProductRankingSnapshotJobParameters(
    RankingPeriod period,
    LocalDate aggregationEndDate,
    int revision
) {

    public static final String PERIOD = "period";
    public static final String AGGREGATION_END_DATE = "aggregationEndDate";
    public static final String REVISION = "revision";
    private static final Set<String> IDENTIFYING_PARAMETERS = Set.of(
        PERIOD,
        AGGREGATION_END_DATE,
        REVISION
    );

    public ProductRankingSnapshotJobParameters {
        Objects.requireNonNull(period, "period must not be null");
        Objects.requireNonNull(aggregationEndDate, "aggregationEndDate must not be null");
        if (period == RankingPeriod.DAILY) {
            throw new IllegalArgumentException("period must be WEEKLY or MONTHLY");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be at least 1");
        }
    }

    public static ProductRankingSnapshotJobParameters from(JobParameters jobParameters) {
        Objects.requireNonNull(jobParameters, "jobParameters must not be null");

        ProductRankingSnapshotJobParameters parameters = from(
            requiredParameter(jobParameters, PERIOD, String.class),
            requiredParameter(jobParameters, AGGREGATION_END_DATE, String.class),
            requiredParameter(jobParameters, REVISION, Long.class)
        );
        validateIdentifyingParameters(jobParameters);
        return parameters;
    }

    public static ProductRankingSnapshotJobParameters from(
        String period,
        String aggregationEndDate,
        Long revision
    ) {
        return new ProductRankingSnapshotJobParameters(
            parsePeriod(period),
            parseAggregationEndDate(aggregationEndDate),
            parseRevision(revision)
        );
    }

    public LocalDate periodStart() {
        return period.periodStart(aggregationEndDate);
    }

    public ProductRankingSnapshotKey toSnapshotKey() {
        return new ProductRankingSnapshotKey(period, aggregationEndDate, revision);
    }

    private static RankingPeriod parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("period is required");
        }

        try {
            RankingPeriod parsed = RankingPeriod.valueOf(period);
            if (parsed == RankingPeriod.DAILY) {
                throw new IllegalArgumentException("period must be WEEKLY or MONTHLY");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("period must be WEEKLY or MONTHLY", exception);
        }
    }

    private static LocalDate parseAggregationEndDate(String aggregationEndDate) {
        if (aggregationEndDate == null || aggregationEndDate.isBlank()) {
            throw new IllegalArgumentException("aggregationEndDate is required");
        }

        try {
            return LocalDate.parse(aggregationEndDate, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                "aggregationEndDate must be a valid yyyyMMdd date",
                exception
            );
        }
    }

    private static int parseRevision(Long revision) {
        if (revision == null) {
            throw new IllegalArgumentException("revision is required");
        }
        if (revision < 1 || revision > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("revision must be between 1 and " + Integer.MAX_VALUE);
        }
        return revision.intValue();
    }

    private static <T> T requiredParameter(
        JobParameters jobParameters,
        String parameterName,
        Class<T> parameterType
    ) {
        JobParameter<?> parameter = jobParameters.getParameter(parameterName);
        if (parameter == null) {
            throw new IllegalArgumentException(parameterName + " is required");
        }
        if (parameter.getType() != parameterType) {
            throw new IllegalArgumentException(
                parameterName + " must be " + parameterType.getSimpleName()
            );
        }
        if (!parameter.isIdentifying()) {
            throw new IllegalArgumentException(parameterName + " must be identifying");
        }
        return parameterType.cast(parameter.getValue());
    }

    private static void validateIdentifyingParameters(JobParameters jobParameters) {
        Set<String> identifyingParameters = jobParameters.getParameters()
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().isIdentifying())
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
        if (!identifyingParameters.equals(IDENTIFYING_PARAMETERS)) {
            throw new IllegalArgumentException(
                "identifying parameters must be exactly period, aggregationEndDate and revision"
            );
        }
    }
}
