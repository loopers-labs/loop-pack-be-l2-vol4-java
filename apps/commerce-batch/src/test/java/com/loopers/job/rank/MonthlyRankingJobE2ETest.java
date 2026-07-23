package com.loopers.job.rank;

import com.loopers.batch.job.rank.MonthlyRankingJobConfig;
import com.loopers.batch.job.rank.RankingPeriod;
import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.rank.ProductRankMonthlyModel;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.infrastructure.rank.ProductRankMonthlyJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME)
class MonthlyRankingJobE2ETest {

    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 7, 15);
    private static final RankingPeriod.Window WINDOW = RankingPeriod.MONTHLY.window(REQUEST_DATE);
    private static final String PERIOD_KEY = RankingPeriod.MONTHLY.periodKey(REQUEST_DATE);

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private ProductRankMonthlyJpaRepository monthlyRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("월간 배치는 해당 달(1일~말일)의 일별 집계를 상품별로 합산·채점해 TOP 100 을 mv_product_rank_monthly 에 적재한다.")
    @Test
    void aggregatesMonthlyTopRanking() throws Exception {
        // arrange — 상품1(달 내 2일 sales 10+10=20), 상품2(달 내 view100), 상품3(달 밖 sales100 → 제외)
        productMetricsJpaRepository.saveAll(List.of(
                ProductMetricsModel.of(1L, WINDOW.start(), 0, 0, 10),
                ProductMetricsModel.of(1L, WINDOW.start().plusDays(19), 0, 0, 10),
                ProductMetricsModel.of(2L, WINDOW.start().plusDays(9), 100, 0, 0),
                ProductMetricsModel.of(3L, WINDOW.start().minusDays(1), 0, 0, 100)
        ));

        // act
        jobLauncherTestUtils.setJob(job);
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobLauncherTestUtils.getUniqueJobParametersBuilder()
                .addLocalDate("requestDate", REQUEST_DATE)
                .toJobParameters());

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<ProductRankMonthlyModel> ranking = monthlyRepository.findByPeriodKeyOrderByRankNo(PERIOD_KEY);
        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).getRankNo()).isEqualTo(1);
        assertThat(ranking.get(0).getProductId()).isEqualTo(1L);
        assertThat(ranking.get(0).getScore()).isCloseTo(14.0, within(1e-9)); // 0.7*20
        assertThat(ranking.get(1).getRankNo()).isEqualTo(2);
        assertThat(ranking.get(1).getProductId()).isEqualTo(2L);
        assertThat(ranking.get(1).getScore()).isCloseTo(10.0, within(1e-9)); // 0.1*100
        assertThat(ranking).noneMatch(row -> row.getProductId().equals(3L)); // 달 밖 상품은 제외
    }
}