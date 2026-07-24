package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.DeleteObsoleteProductRankingCandidatesTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@ConditionalOnProperty(
    name = "spring.batch.job.name",
    havingValue = ProductRankingCleanupJobConfig.JOB_NAME
)
@RequiredArgsConstructor
@Configuration
public class ProductRankingCleanupJobConfig {

    public static final String JOB_NAME = "productRankingCleanupJob";
    public static final String DELETE_OBSOLETE_CANDIDATES_STEP_NAME =
        "deleteObsoleteProductRankingCandidatesStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ProductRankingCleanupJobParameterValidator parameterValidator;
    private final DeleteObsoleteProductRankingCandidatesTasklet tasklet;

    @Bean(JOB_NAME)
    public Job productRankingCleanupJob(
        @Qualifier(DELETE_OBSOLETE_CANDIDATES_STEP_NAME) Step cleanupStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .validator(parameterValidator)
            .start(cleanupStep)
            .listener(jobListener)
            .build();
    }

    @Bean(DELETE_OBSOLETE_CANDIDATES_STEP_NAME)
    public Step deleteObsoleteProductRankingCandidatesStep() {
        return new StepBuilder(
            DELETE_OBSOLETE_CANDIDATES_STEP_NAME,
            jobRepository
        )
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }
}
