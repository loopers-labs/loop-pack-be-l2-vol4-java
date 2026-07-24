package com.loopers.batch.job.ranking;

import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ProductRankingCleanupJobParameters(long targetSnapshotId) {

    public static final String TARGET_SNAPSHOT_ID = "targetSnapshotId";
    private static final Set<String> IDENTIFYING_PARAMETERS =
        Set.of(TARGET_SNAPSHOT_ID);

    public ProductRankingCleanupJobParameters {
        if (targetSnapshotId < 1) {
            throw new IllegalArgumentException(
                "targetSnapshotId must be positive"
            );
        }
    }

    public static ProductRankingCleanupJobParameters from(
        JobParameters jobParameters
    ) {
        Objects.requireNonNull(jobParameters, "jobParameters must not be null");

        ProductRankingCleanupJobParameters parameters = from(
            requiredTargetSnapshotId(jobParameters)
        );
        validateIdentifyingParameters(jobParameters);
        return parameters;
    }

    public static ProductRankingCleanupJobParameters from(
        Long targetSnapshotId
    ) {
        if (targetSnapshotId == null) {
            throw new IllegalArgumentException("targetSnapshotId is required");
        }
        return new ProductRankingCleanupJobParameters(targetSnapshotId);
    }

    private static Long requiredTargetSnapshotId(JobParameters jobParameters) {
        JobParameter<?> parameter =
            jobParameters.getParameter(TARGET_SNAPSHOT_ID);
        if (parameter == null) {
            throw new IllegalArgumentException("targetSnapshotId is required");
        }
        if (parameter.getType() != Long.class) {
            throw new IllegalArgumentException("targetSnapshotId must be Long");
        }
        if (!parameter.isIdentifying()) {
            throw new IllegalArgumentException(
                "targetSnapshotId must be identifying"
            );
        }
        return (Long) parameter.getValue();
    }

    private static void validateIdentifyingParameters(
        JobParameters jobParameters
    ) {
        Set<String> identifyingParameters = jobParameters.getParameters()
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().isIdentifying())
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
        if (!identifyingParameters.equals(IDENTIFYING_PARAMETERS)) {
            throw new IllegalArgumentException(
                "identifying parameters must be exactly targetSnapshotId"
            );
        }
    }
}
