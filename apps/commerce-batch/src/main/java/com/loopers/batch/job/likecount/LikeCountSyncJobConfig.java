package com.loopers.batch.job.likecount;

import com.loopers.batch.job.likecount.step.LikeCountSyncTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = LikeCountSyncJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class LikeCountSyncJobConfig {
    public static final String JOB_NAME = "likeCountSyncJob";
    private static final String STEP_NAME = "likeCountSyncStep";

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final LikeCountSyncTasklet likeCountSyncTasklet;

    @Bean(JOB_NAME)
    public Job likeCountSyncJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(likeCountSyncStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step likeCountSyncStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(likeCountSyncTasklet, new ResourcelessTransactionManager())
                .listener(stepMonitorListener)
                .build();
    }
}
