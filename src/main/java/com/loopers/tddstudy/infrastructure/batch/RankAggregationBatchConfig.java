package com.loopers.tddstudy.infrastructure.batch;

import com.loopers.tddstudy.application.ranking.RankAggregationService;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Configuration
public class RankAggregationBatchConfig {

    private static final DateTimeFormatter BASE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Bean
    public Step weeklyRankStep(JobRepository jobRepository,
                               PlatformTransactionManager txManager,
                               RankAggregationService aggregationService) {
        return new StepBuilder("weeklyRankStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    aggregationService.aggregateWeekly(baseDateOf(chunkContext));
                    return RepeatStatus.FINISHED;
                }, txManager)
                .build();
    }

    @Bean
    public Step monthlyRankStep(JobRepository jobRepository,
                                PlatformTransactionManager txManager,
                                RankAggregationService aggregationService) {
        return new StepBuilder("monthlyRankStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    aggregationService.aggregateMonthly(baseDateOf(chunkContext));
                    return RepeatStatus.FINISHED;
                }, txManager)
                .build();
    }

    @Bean
    public Job rankAggregationJob(JobRepository jobRepository,
                                  Step weeklyRankStep,
                                  Step monthlyRankStep) {
        return new JobBuilder("rankAggregationJob", jobRepository)
                .start(weeklyRankStep)
                .next(monthlyRankStep)
                .build();
    }

    private static LocalDate baseDateOf(ChunkContext chunkContext) {
        String baseDate = (String) chunkContext.getStepContext()
                .getJobParameters().get("baseDate");
        return LocalDate.parse(baseDate, BASE_DATE_FORMAT);
    }
}
