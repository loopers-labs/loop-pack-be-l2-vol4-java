package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.RankingDeleteTasklet;
import com.loopers.batch.job.ranking.step.RankingItemReaderFactory;
import com.loopers.batch.job.ranking.step.RankingItemWriterFactory;
import com.loopers.batch.job.ranking.step.RankingMvRow;
import com.loopers.batch.job.ranking.step.RankingRow;
import com.loopers.batch.job.ranking.step.RankingRowProcessor;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.ranking.domain.RankingPeriod;
import com.loopers.ranking.domain.RankingScoreWeights;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;

/**
 * 주간 랭킹 MV 적재. 월간과 로직이 같고 기간·대상 테이블만 달라 JobConfig 만 두 벌 둔다.
 * RunIdIncrementer 는 붙이지 않는다 — targetDate 가 이미 식별 파라미터라 할 일이 없고,
 * 붙이면 동시 실행·완료 재실행 가드가 약해진다.
 * 트랜잭션 매니저는 실제 DataSource 것을 쓴다. DemoJobConfig 의 ResourcelessTransactionManager 를
 * 복사하면 JDBC 쓰기가 트랜잭션에 묶이지 않는다.
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final RankingPeriod PERIOD = RankingPeriod.WEEKLY;
    private static final int CHUNK_SIZE = 100;

    private static final String DELETE_STEP = "weeklyRankingDeleteStep";
    private static final String DELETE_TASKLET = "weeklyRankingDeleteTasklet";
    private static final String LOAD_STEP = "weeklyRankingLoadStep";
    private static final String READER = "weeklyRankingReader";
    private static final String PROCESSOR = "weeklyRankingProcessor";
    private static final String WRITER = "weeklyRankingWriter";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final RankingScoreWeights weights;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob(@Qualifier(DELETE_STEP) Step deleteStep,
                                @Qualifier(LOAD_STEP) Step loadStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new TargetDateValidator())
                .start(deleteStep)
                .next(loadStep)
                .listener(jobListener)
                .build();
    }

    @Bean(DELETE_STEP)
    public Step weeklyRankingDeleteStep(@Qualifier(DELETE_TASKLET) Tasklet deleteTasklet) {
        return new StepBuilder(DELETE_STEP, jobRepository)
                .tasklet(deleteTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean(DELETE_TASKLET)
    public Tasklet weeklyRankingDeleteTasklet(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return new RankingDeleteTasklet(jdbcTemplate, PERIOD, TargetDateValidator.parse(targetDate));
    }

    @Bean(LOAD_STEP)
    public Step weeklyRankingLoadStep(@Qualifier(READER) ItemStreamReader<RankingRow> reader,
                                      @Qualifier(PROCESSOR) ItemProcessor<RankingRow, RankingMvRow> processor,
                                      @Qualifier(WRITER) ItemWriter<RankingMvRow> writer) {
        return new StepBuilder(LOAD_STEP, jobRepository)
                .<RankingRow, RankingMvRow>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean(READER)
    public ItemStreamReader<RankingRow> weeklyRankingReader(@Value("#{jobParameters['targetDate']}") String targetDate) {
        RankingPeriod.Range range = PERIOD.resolve(TargetDateValidator.parse(targetDate));
        return RankingItemReaderFactory.cursor(dataSource, range, weights);
    }

    @StepScope
    @Bean(PROCESSOR)
    public ItemProcessor<RankingRow, RankingMvRow> weeklyRankingProcessor(
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        LocalDate date = TargetDateValidator.parse(targetDate);
        return new RankingRowProcessor(PERIOD.resolve(date).periodKey());
    }

    @Bean(WRITER)
    public ItemWriter<RankingMvRow> weeklyRankingWriter() {
        return RankingItemWriterFactory.forPeriod(dataSource, PERIOD);
    }
}
