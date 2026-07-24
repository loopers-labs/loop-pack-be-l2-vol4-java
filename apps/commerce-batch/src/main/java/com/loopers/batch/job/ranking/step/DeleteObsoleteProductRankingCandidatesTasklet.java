package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.ProductRankingCleanupJobConfig;
import com.loopers.batch.job.ranking.ProductRankingCleanupJobParameters;
import com.loopers.ranking.application.ProductRankingCleanupService;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@StepScope
@ConditionalOnProperty(
    name = "spring.batch.job.name",
    havingValue = ProductRankingCleanupJobConfig.JOB_NAME
)
@Component
public class DeleteObsoleteProductRankingCandidatesTasklet implements Tasklet {

    private static final int DELETE_BATCH_SIZE = 1_000;

    private final ProductRankingCleanupService cleanupService;
    private final long targetSnapshotId;
    private ProductRankingSnapshotHeader cleanupTarget;

    public DeleteObsoleteProductRankingCandidatesTasklet(
        ProductRankingCleanupService cleanupService,
        @Value("#{jobParameters['targetSnapshotId']}") Long targetSnapshotId
    ) {
        this.cleanupService = cleanupService;
        this.targetSnapshotId = ProductRankingCleanupJobParameters.from(
            targetSnapshotId
        ).targetSnapshotId();
    }

    @Override
    public RepeatStatus execute(
        StepContribution contribution,
        ChunkContext chunkContext
    ) {
        if (cleanupTarget == null) {
            cleanupTarget =
                cleanupService.validateCleanupTarget(targetSnapshotId);
        }

        int deleted = cleanupService.deleteCandidates(
            cleanupTarget,
            DELETE_BATCH_SIZE
        );
        if (deleted == DELETE_BATCH_SIZE) {
            return RepeatStatus.CONTINUABLE;
        }
        return RepeatStatus.FINISHED;
    }
}
