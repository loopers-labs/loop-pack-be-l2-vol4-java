package com.loopers.batch.job.ranking;

import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

// Hides: chunk-2 transaction boundaries and Spring Batch restart metadata wiring.
@Configuration
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
public class WeeklyRankingJobConfig {
    public static final String JOB_NAME = "weeklyRankingJob";
    public static final String STEP_NAME = "weeklyRankingStep";

    @Bean(JOB_NAME)
    Job weeklyRankingJob(JobRepository jobRepository, @Qualifier(STEP_NAME) Step weeklyRankingStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(weeklyRankingStep).build();
    }

    @Bean(STEP_NAME)
    Step weeklyRankingStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        RankingItemReader rankingItemReader,
        RankingItemProcessor rankingItemProcessor,
        RankingItemWriter rankingItemWriter
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
            .<RankingItemReader.SourceRow, RankingItemReader.SourceRow>chunk(2, transactionManager)
            .reader(rankingItemReader)
            .processor(rankingItemProcessor)
            .writer(rankingItemWriter)
            .build();
    }

    @Bean
    @StepScope
    RankingItemReader rankingItemReader(DataSource dataSource) {
        return new RankingItemReader(new JdbcTemplate(dataSource));
    }

    @Bean
    @StepScope
    RankingItemProcessor rankingItemProcessor(
        @Value("#{jobParameters['injectFailure'] ?: false}") boolean injectFailure
    ) {
        return new RankingItemProcessor(injectFailure);
    }

    @Bean
    @StepScope
    RankingItemWriter rankingItemWriter(DataSource dataSource) {
        return new RankingItemWriter(new JdbcTemplate(dataSource));
    }
}
