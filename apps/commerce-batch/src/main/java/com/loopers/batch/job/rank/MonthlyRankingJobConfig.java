package com.loopers.batch.job.rank;

import com.loopers.batch.job.rank.step.RankAggregateReaderFactory;
import com.loopers.batch.job.rank.step.RankScoreProcessor;
import com.loopers.batch.job.rank.step.RankTopNTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.rank.ProductRankMonthlyModel;
import com.loopers.domain.rank.ProductRankScoreModel;
import com.loopers.infrastructure.rank.ProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.rank.ProductRankScoreJpaRepository;
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
 * 월간 랭킹 집계 Job. 일별 product_metrics 를 requestDate 가 속한 달(1일~말일) 범위로 합산·채점(Chunk)해 staging 에 쌓고,
 * TOP 100 을 잘라 mv_product_rank_monthly 에 적재(Tasklet)한다. spring.batch.job.name 으로 선택 실행한다.
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final RankingPeriod PERIOD = RankingPeriod.MONTHLY;
    private static final int CHUNK_SIZE = 500;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;
    private final ProductRankScoreJpaRepository scoreRepository;
    private final ProductRankMonthlyJpaRepository monthlyRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(monthlyRankAggregateStep())
                .next(monthlyRankTopNStep())
                .listener(jobListener)
                .build();
    }

    @Bean
    public Step monthlyRankAggregateStep() {
        return new StepBuilder("monthlyRankAggregateStep", jobRepository)
                .<MetricsAggregate, ProductRankScoreModel>chunk(CHUNK_SIZE, transactionManager)
                .reader(monthlyRankAggregateReader(null))
                .processor(monthlyRankScoreProcessor(null))
                .writer(monthlyRankStagingWriter())
                .listener(stepMonitorListener)
                .build();
    }

    @Bean
    public Step monthlyRankTopNStep() {
        return new StepBuilder("monthlyRankTopNStep", jobRepository)
                .tasklet(monthlyRankTopNTasklet(null), transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<MetricsAggregate> monthlyRankAggregateReader(
            @Value("#{jobParameters['requestDate']}") LocalDate requestDate) {
        RankingPeriod.Window window = PERIOD.window(requestDate);
        return RankAggregateReaderFactory.create(dataSource, window.start(), window.end());
    }

    @Bean
    @StepScope
    public RankScoreProcessor monthlyRankScoreProcessor(
            @Value("#{jobParameters['requestDate']}") LocalDate requestDate) {
        return new RankScoreProcessor(PERIOD.periodKey(requestDate));
    }

    @Bean
    public JpaItemWriter<ProductRankScoreModel> monthlyRankStagingWriter() {
        return new JpaItemWriterBuilder<ProductRankScoreModel>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    @StepScope
    public RankTopNTasklet monthlyRankTopNTasklet(
            @Value("#{jobParameters['requestDate']}") LocalDate requestDate) {
        String periodKey = PERIOD.periodKey(requestDate);
        RankMvReplacer replacer = (key, rows) -> {
            monthlyRepository.deleteByPeriodKey(key);
            ZonedDateTime now = ZonedDateTime.now();
            monthlyRepository.saveAll(rows.stream()
                    .map(row -> ProductRankMonthlyModel.of(key, row.rankNo(), row.productId(), row.score(), now))
                    .toList());
        };
        return new RankTopNTasklet(periodKey, scoreRepository, replacer);
    }
}