package com.loopers.batch.job.rank;

import com.loopers.batch.job.rank.step.RankAggregateReaderFactory;
import com.loopers.batch.job.rank.step.RankScoreProcessor;
import com.loopers.batch.job.rank.step.RankTopNTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.rank.ProductRankScoreModel;
import com.loopers.domain.rank.ProductRankWeeklyModel;
import com.loopers.infrastructure.rank.ProductRankScoreJpaRepository;
import com.loopers.infrastructure.rank.ProductRankWeeklyJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * 주간 랭킹 집계 Job. 일별 product_metrics 를 requestDate 가 속한 ISO 주(월~일) 범위로 합산·채점(Chunk)해 staging 에 쌓고,
 * TOP 100 을 잘라 mv_product_rank_weekly 에 적재(Tasklet)한다. spring.batch.job.name 으로 선택 실행한다.
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final RankingPeriod PERIOD = RankingPeriod.WEEKLY;
    private static final int CHUNK_SIZE = 500;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;
    private final ProductRankScoreJpaRepository scoreRepository;
    private final ProductRankWeeklyJpaRepository weeklyRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(weeklyRankAggregateStep())
                .next(weeklyRankTopNStep())
                .listener(jobListener)
                .build();
    }

    @Bean
    public Step weeklyRankAggregateStep() {
        return new StepBuilder("weeklyRankAggregateStep", jobRepository)
                .<MetricsAggregate, ProductRankScoreModel>chunk(CHUNK_SIZE, transactionManager)
                .reader(weeklyRankAggregateReader(null))
                .processor(weeklyRankScoreProcessor(null))
                .writer(weeklyRankStagingWriter())
                .listener(stepMonitorListener)
                .build();
    }

    @Bean
    public Step weeklyRankTopNStep() {
        return new StepBuilder("weeklyRankTopNStep", jobRepository)
                .tasklet(weeklyRankTopNTasklet(null), transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<MetricsAggregate> weeklyRankAggregateReader(
            @Value("#{jobParameters['requestDate']}") LocalDate requestDate) {
        RankingPeriod.Window window = PERIOD.window(requestDate);
        return RankAggregateReaderFactory.create(dataSource, window.start(), window.end());
    }

    @Bean
    @StepScope
    public RankScoreProcessor weeklyRankScoreProcessor(
            @Value("#{jobParameters['requestDate']}") LocalDate requestDate) {
        return new RankScoreProcessor(PERIOD.periodKey(requestDate));
    }

    @Bean
    public JpaItemWriter<ProductRankScoreModel> weeklyRankStagingWriter() {
        return new JpaItemWriterBuilder<ProductRankScoreModel>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    @StepScope
    public RankTopNTasklet weeklyRankTopNTasklet(
            @Value("#{jobParameters['requestDate']}") LocalDate requestDate) {
        String periodKey = PERIOD.periodKey(requestDate);
        RankMvReplacer replacer = (key, rows) -> {
            weeklyRepository.deleteByPeriodKey(key);
            ZonedDateTime now = ZonedDateTime.now();
            weeklyRepository.saveAll(rows.stream()
                    .map(row -> ProductRankWeeklyModel.of(key, row.rankNo(), row.productId(), row.score(), now))
                    .toList());
        };
        return new RankTopNTasklet(periodKey, scoreRepository, replacer);
    }
}