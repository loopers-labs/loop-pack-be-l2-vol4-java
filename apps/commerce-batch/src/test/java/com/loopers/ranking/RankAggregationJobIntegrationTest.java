package com.loopers.ranking;

import com.loopers.batch.job.ranking.RankAggregationJobConfig;
import com.loopers.metrics.domain.ProductMetricsModel;
import com.loopers.metrics.infrastructure.ProductMetricsJpaRepository;
import com.loopers.ranking.domain.WeeklyProductRankModel;
import com.loopers.ranking.infrastructure.MonthlyProductRankJpaRepository;
import com.loopers.ranking.infrastructure.WeeklyProductRankJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + RankAggregationJobConfig.JOB_NAME)
class RankAggregationJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(RankAggregationJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private WeeklyProductRankJpaRepository weeklyRepository;

    @Autowired
    private MonthlyProductRankJpaRepository monthlyRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private JobParameters params(String period) {
        return new JobParametersBuilder()
            .addString("period", period)
            .addLong("run", System.nanoTime()) // 매 실행 고유 인스턴스 보장
            .toJobParameters();
    }

    @DisplayName("주간 랭킹 집계 Job은,")
    @Nested
    class Weekly {

        @DisplayName("product_metrics를 점수순으로 집계해 주간 MV에 rank 1..N으로 적재한다.")
        @Test
        void aggregatesIntoWeeklyMv_orderedByScore() throws Exception {
            // arrange — A(view100)=10.0 > B(like10)=2.0 > C(sales1000)≈1.8
            productMetricsJpaRepository.save(new ProductMetricsModel(1L, 0, 0, 100)); // A
            productMetricsJpaRepository.save(new ProductMetricsModel(2L, 10, 0, 0));  // B
            productMetricsJpaRepository.save(new ProductMetricsModel(3L, 0, 1000, 0)); // C
            jobLauncherTestUtils.setJob(job);

            // act
            var execution = jobLauncherTestUtils.launchJob(params("weekly"));

            // assert
            assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
            List<WeeklyProductRankModel> ranks = weeklyRepository.findAllByOrderByRankAsc();
            assertThat(ranks).hasSize(3);
            assertThat(ranks).extracting(WeeklyProductRankModel::getRank).containsExactly(1, 2, 3);
            assertThat(ranks).extracting(WeeklyProductRankModel::getProductId).containsExactly(1L, 2L, 3L);
            assertThat(ranks.get(0).getScore()).isGreaterThan(ranks.get(1).getScore());
        }

        @DisplayName("재실행하면 MV가 중복 없이 최신 값으로 교체된다(멱등).")
        @Test
        void replacesMv_whenRerun() throws Exception {
            // arrange
            productMetricsJpaRepository.save(new ProductMetricsModel(1L, 0, 0, 100));
            productMetricsJpaRepository.save(new ProductMetricsModel(2L, 10, 0, 0));
            jobLauncherTestUtils.setJob(job);

            // act — 두 번 실행
            jobLauncherTestUtils.launchJob(params("weekly"));
            var second = jobLauncherTestUtils.launchJob(params("weekly"));

            // assert — 4행이 아니라 2행만 존재
            assertThat(second.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
            assertThat(weeklyRepository.findAllByOrderByRankAsc()).hasSize(2);
        }

        @DisplayName("집계 대상(product_metrics)이 비어 있으면, 새로 구울 게 없으므로 기존 MV를 지우지 않고 유지한다.")
        @Test
        void keepsExistingMv_whenSourceIsEmpty() throws Exception {
            // arrange — product_metrics는 비우고, 기존 MV에 마지막 정상 판 1행을 심어둔다
            weeklyRepository.save(new WeeklyProductRankModel(999L, 1, 123.0));
            jobLauncherTestUtils.setJob(job);

            // act
            var execution = jobLauncherTestUtils.launchJob(params("weekly"));

            // assert — Job은 성공하고 기존 판이 그대로 남는다(비우기 미발생)
            assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
            List<WeeklyProductRankModel> remaining = weeklyRepository.findAllByOrderByRankAsc();
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getProductId()).isEqualTo(999L);
            assertThat(remaining.get(0).getScore()).isEqualTo(123.0);
        }
    }

    @DisplayName("월간 랭킹 집계 Job은, period=monthly면 월간 MV에 적재한다.")
    @Test
    void aggregatesIntoMonthlyMv() throws Exception {
        // arrange
        productMetricsJpaRepository.save(new ProductMetricsModel(1L, 0, 0, 100));
        jobLauncherTestUtils.setJob(job);

        // act
        var execution = jobLauncherTestUtils.launchJob(params("monthly"));

        // assert
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        assertThat(monthlyRepository.findAllByOrderByRankAsc()).hasSize(1);
        assertThat(weeklyRepository.findAllByOrderByRankAsc()).isEmpty();
    }
}
