package com.loopers.tddstudy.infrastructure.batch;

import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDaily;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import com.loopers.tddstudy.infrastructure.ranking.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(RankAggregationBatchIntegrationTest.TestConfig.class)
class RankAggregationBatchIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        JobLauncherTestUtils rankJobLauncherTestUtils(Job rankAggregationJob,
                                                      JobLauncher jobLauncher,
                                                      JobRepository jobRepository) {
            JobLauncherTestUtils utils = new JobLauncherTestUtils();
            utils.setJob(rankAggregationJob);
            utils.setJobLauncher(jobLauncher);
            utils.setJobRepository(jobRepository);
            return utils;
        }
    }

    @Autowired JobLauncherTestUtils rankJobLauncherTestUtils;
    @Autowired ProductMetricsDailyJpaRepository dailyRepository;
    @Autowired ProductRankWeeklyJpaRepository weeklyRepository;
    @Autowired ProductRankMonthlyJpaRepository monthlyRepository;

    @BeforeEach
    void setUp() {
        weeklyRepository.deleteAll();
        monthlyRepository.deleteAll();
        dailyRepository.deleteAll();
    }

    private JobParameters params(String baseDate) {
        return new JobParametersBuilder()
                .addString("baseDate", baseDate)
                .addLong("run.id", System.nanoTime())
                .toJobParameters();
    }

    @Test
    void 배치를_돌리면_주간과_월간_랭킹이_함께_만들어진다() throws Exception {
        // given: 6월 말~7월 초 데이터
        dailyRepository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 6, 29), 0, 10, 0));
        dailyRepository.save(new ProductMetricsDaily(2L, LocalDate.of(2026, 6, 30), 0, 5, 0));

        // when: 7/6(월) 기준 실행
        JobExecution execution = rankJobLauncherTestUtils.launchJob(params("20260706"));

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 주간: 직전 주 6/29~7/5 → 2026-W27
        List<ProductRankWeekly> weekly =
                weeklyRepository.findByPeriodKeyOrderByRankNoAsc("2026-W27", PageRequest.of(0, 100));
        assertThat(weekly).hasSize(2);
        assertThat(weekly.get(0).getProductId()).isEqualTo(1L);
        assertThat(weekly.get(0).getRankNo()).isEqualTo(1);

        // 월간: 직전 달 6/1~6/30 → 2026-06
        List<ProductRankMonthly> monthly =
                monthlyRepository.findByPeriodKeyOrderByRankNoAsc("2026-06", PageRequest.of(0, 100));
        assertThat(monthly).hasSize(2);
        assertThat(monthly.get(0).getProductId()).isEqualTo(1L);
    }

    @Test
    void 파라미터_날짜를_바꾸면_다른_기간이_집계된다() throws Exception {
        dailyRepository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 10), 0, 10, 0));

        // 8/1 기준 → 월간은 7월
        rankJobLauncherTestUtils.launchJob(params("20260801"));

        List<ProductRankMonthly> july =
                monthlyRepository.findByPeriodKeyOrderByRankNoAsc("2026-07", PageRequest.of(0, 100));
        assertThat(july).hasSize(1);
        assertThat(july.get(0).getProductId()).isEqualTo(1L);
    }

    @Test
    void 같은_기간을_두_번_집계해도_중복되지_않는다() throws Exception {
        dailyRepository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 6, 29), 0, 10, 0));

        rankJobLauncherTestUtils.launchJob(params("20260706"));
        rankJobLauncherTestUtils.launchJob(params("20260706"));

        assertThat(weeklyRepository.count()).isEqualTo(1);
    }
}
