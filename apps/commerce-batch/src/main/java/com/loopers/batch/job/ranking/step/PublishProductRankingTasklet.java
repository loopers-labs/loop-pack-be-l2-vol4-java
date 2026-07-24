package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.batch.job.ranking.ProductRankingSnapshotJobParameters;
import com.loopers.ranking.application.ProductRankingPublicationService;
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
    havingValue = ProductRankingSnapshotJobConfig.JOB_NAME
)
@Component
public class PublishProductRankingTasklet implements Tasklet {

    private final ProductRankingPublicationService publicationService;
    private final ProductRankingSnapshotJobParameters parameters;

    public PublishProductRankingTasklet(
        ProductRankingPublicationService publicationService,
        @Value("#{jobParameters['period']}") String period,
        @Value("#{jobParameters['aggregationEndDate']}") String aggregationEndDate,
        @Value("#{jobParameters['revision']}") Long revision
    ) {
        this.publicationService = publicationService;
        this.parameters = ProductRankingSnapshotJobParameters.from(
            period,
            aggregationEndDate,
            revision
        );
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        publicationService.publish(parameters.toSnapshotKey());
        return RepeatStatus.FINISHED;
    }
}
