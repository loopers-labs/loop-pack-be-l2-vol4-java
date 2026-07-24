package com.loopers.infrastructure.ranking;

import com.loopers.batch.job.ranking.ProductRankingJobConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.batch.job.enabled=false",
    "spring.batch.job.name=" + ProductRankingJobConfig.JOB_NAME
})
@SpringBatchTest
class ProductRankingAggregationJobIntegrationTest {

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean(name = "defaultRedisConnectionFactory")
    private LettuceConnectionFactory defaultRedisConnectionFactory;

    @MockitoBean(name = "masterRedisConnectionFactory")
    private LettuceConnectionFactory masterRedisConnectionFactory;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(ProductRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductMetricsBatchJpaRepository productMetricsRepository;

    @Autowired
    private ProductBatchJpaRepository productRepository;

    @Autowired
    private ProductRankWeeklyMvJpaRepository weeklyRepository;

    @Autowired
    private ProductRankMonthlyMvJpaRepository monthlyRepository;

    @Autowired
    private ProductRankBatchRunJpaRepository batchRunRepository;

    @BeforeEach
    void setUp() {
        weeklyRepository.deleteAll();
        monthlyRepository.deleteAll();
        productMetricsRepository.deleteAll();
        productRepository.deleteAll();
        batchRunRepository.deleteAll();
    }

    @Test
    @DisplayName("주간 기간의 product_metrics를 집계해 active weekly Snapshot Top 100을 생성한다.")
    void launchJob_WhenWeeklyMetricsExist_ShouldCreateActiveWeeklySnapshot() throws Exception {
        jobLauncherTestUtils.setJob(job);
        productRepository.save(new ProductBatchJpaEntity(1L, false));
        productRepository.save(new ProductBatchJpaEntity(2L, false));
        productRepository.save(new ProductBatchJpaEntity(3L, true));
        productMetricsRepository.save(new ProductMetricsBatchJpaEntity(1L, LocalDate.of(2026, 7, 20), 1L, 10.0));
        productMetricsRepository.save(new ProductMetricsBatchJpaEntity(2L, LocalDate.of(2026, 7, 21), 1L, 20.0));
        productMetricsRepository.save(new ProductMetricsBatchJpaEntity(3L, LocalDate.of(2026, 7, 22), 2L, 40.0));
        productMetricsRepository.save(new ProductMetricsBatchJpaEntity(4L, LocalDate.of(2026, 7, 23), 3L, 100.0));

        var jobParameters = new JobParametersBuilder()
            .addString("period", "WEEKLY")
            .addString("startDate", "20260720")
            .addString("endDate", "20260726")
            .addLong("run.id", System.nanoTime())
            .toJobParameters();

        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        var snapshots = weeklyRepository.findByRankStartDateAndRankEndDateAndActiveTrueOrderByRankNoAsc(
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            PageRequest.of(0, 20)
        );
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).extracting(ProductRankWeeklyMvJpaEntity::getProductId).containsExactly(2L, 1L);
        assertThat(snapshots).extracting(ProductRankWeeklyMvJpaEntity::getRankNo).containsExactly(1, 2);
    }

    @Test
    @DisplayName("월간 기간의 product_metrics를 집계해 active monthly Snapshot Top 100을 생성한다.")
    void launchJob_WhenMonthlyMetricsExist_ShouldCreateActiveMonthlySnapshot() throws Exception {
        jobLauncherTestUtils.setJob(job);
        productRepository.save(new ProductBatchJpaEntity(11L, false));
        productRepository.save(new ProductBatchJpaEntity(12L, false));
        productMetricsRepository.save(new ProductMetricsBatchJpaEntity(11L, LocalDate.of(2026, 7, 1), 11L, 15.0));
        productMetricsRepository.save(new ProductMetricsBatchJpaEntity(12L, LocalDate.of(2026, 7, 31), 11L, 5.0));
        productMetricsRepository.save(new ProductMetricsBatchJpaEntity(13L, LocalDate.of(2026, 7, 15), 12L, 25.0));

        var jobParameters = new JobParametersBuilder()
            .addString("period", "MONTHLY")
            .addString("startDate", "20260701")
            .addString("endDate", "20260731")
            .addLong("run.id", System.nanoTime())
            .toJobParameters();

        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        var snapshots = monthlyRepository.findByRankStartDateAndRankEndDateAndActiveTrueOrderByRankNoAsc(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PageRequest.of(0, 20)
        );
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).extracting(ProductRankMonthlyMvJpaEntity::getProductId).containsExactly(12L, 11L);
        assertThat(snapshots).extracting(ProductRankMonthlyMvJpaEntity::getRankNo).containsExactly(1, 2);
    }
}
