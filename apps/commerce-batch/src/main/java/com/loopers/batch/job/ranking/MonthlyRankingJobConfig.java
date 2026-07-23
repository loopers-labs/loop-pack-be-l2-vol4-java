package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.RankingDeleteTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.ranking.domain.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 월간 랭킹 MV 적재. 주간과 기간·대상 테이블만 다르다. */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String DELETE_STEP = "monthlyRankingDeleteStep";
    private static final String DELETE_TASKLET = "monthlyRankingDeleteTasklet";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob(@Qualifier(DELETE_STEP) Step deleteStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new TargetDateValidator())
                .start(deleteStep)
                .listener(jobListener)
                .build();
    }

    @Bean(DELETE_STEP)
    public Step monthlyRankingDeleteStep(@Qualifier(DELETE_TASKLET) Tasklet deleteTasklet) {
        return new StepBuilder(DELETE_STEP, jobRepository)
                .tasklet(deleteTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean(DELETE_TASKLET)
    public Tasklet monthlyRankingDeleteTasklet(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return new RankingDeleteTasklet(jdbcTemplate, RankingPeriod.MONTHLY, TargetDateValidator.parse(targetDate));
    }
}
