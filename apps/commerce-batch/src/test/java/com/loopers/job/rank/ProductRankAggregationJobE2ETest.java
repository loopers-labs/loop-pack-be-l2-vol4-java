package com.loopers.job.rank;

import com.loopers.batch.job.rank.ProductRankAggregationJobConfig;
import com.loopers.domain.rank.MonthlyProductRankModel;
import com.loopers.domain.rank.ProductRankSnapshotModel;
import com.loopers.domain.rank.WeeklyProductRankModel;
import com.loopers.infrastructure.rank.MonthlyProductRankJpaRepository;
import com.loopers.infrastructure.rank.WeeklyProductRankJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주간·월간 랭킹 집계 배치 E2E — 일간 롤업(product_metrics_daily)을 시드하고 Job을 실행해
 * MV(mv_product_rank_weekly / mv_product_rank_monthly)에 TOP 100이 순위·점수대로 적재되는지 검증한다.
 *
 * <p>product_metrics_daily는 streamer 소유 테이블이라 batch 엔티티 스캔에 없다(배치는 읽기만 함) →
 * 테스트에서 JdbcTemplate으로 원천 테이블을 만들어 시드한다. Testcontainers(MySQL)라 Docker가 필요하다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + ProductRankAggregationJobConfig.JOB_NAME)
class ProductRankAggregationJobE2ETest {

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(ProductRankAggregationJobConfig.JOB_NAME)
    private Job job;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WeeklyProductRankJpaRepository weeklyRepository;
    @Autowired private MonthlyProductRankJpaRepository monthlyRepository;

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 7, 21);

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS product_metrics_daily ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "product_id BIGINT NOT NULL, "
                + "metric_date DATE NOT NULL, "
                + "view_count BIGINT NOT NULL DEFAULT 0, "
                + "like_count BIGINT NOT NULL DEFAULT 0, "
                + "sales_count BIGINT NOT NULL DEFAULT 0)"
        );
        jdbcTemplate.update("DELETE FROM product_metrics_daily");
        weeklyRepository.deleteAllInBatch();
        monthlyRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM product_metrics_daily");
        weeklyRepository.deleteAllInBatch();
        monthlyRepository.deleteAllInBatch();
    }

    private void seed(Long productId, LocalDate date, long views, long likes, long sales) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, sales_count) VALUES (?,?,?,?,?)",
            productId, date, views, likes, sales
        );
    }

    @DisplayName("주간 창(baseDate-6..baseDate) 내 롤업을 합산해 점수 내림차순 TOP 순위로 MV에 적재한다.")
    @Test
    void aggregatesWeeklyRanking() throws Exception {
        // arrange — 주간 창 안: product 1(판매 강함), product 2(조회만), 창 밖(8일 전) product 3
        seed(1L, BASE_DATE, 0, 0, 10);              // score = 6.0
        seed(1L, BASE_DATE.minusDays(3), 0, 0, 0);  // 같은 상품 다른 날 (합산 확인용, 0)
        seed(2L, BASE_DATE.minusDays(1), 50, 0, 0); // score = 5.0
        seed(3L, BASE_DATE.minusDays(7), 0, 0, 100); // 창 밖 → 제외
        jobLauncherTestUtils.setJob(job);

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(
            new JobParametersBuilder(jobLauncherTestUtils.getUniqueJobParameters()).addString("baseDate", BASE_DATE.toString()).toJobParameters()
        );

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<WeeklyProductRankModel> weekly = weeklyRepository.findAll().stream()
            .sorted(Comparator.comparingInt(ProductRankSnapshotModel::getRankNo))
            .toList();
        assertThat(weekly).hasSize(2); // 창 밖 product 3 제외
        assertThat(weekly.get(0).getRankNo()).isEqualTo(1);
        assertThat(weekly.get(0).getProductId()).isEqualTo(1L);
        assertThat(weekly.get(0).getScore()).isEqualTo(6.0);
        assertThat(weekly.get(0).getPeriodStart()).isEqualTo(BASE_DATE.minusDays(6));
        assertThat(weekly.get(0).getPeriodEnd()).isEqualTo(BASE_DATE);
        assertThat(weekly.get(1).getRankNo()).isEqualTo(2);
        assertThat(weekly.get(1).getProductId()).isEqualTo(2L);
    }

    @DisplayName("월간 창(baseDate-29..baseDate)은 주간 창 밖 데이터까지 포함해 집계한다.")
    @Test
    void aggregatesMonthlyRankingWithWiderWindow() throws Exception {
        // arrange — 주간 창 밖(20일 전)이지만 월간 창 안인 product 3
        seed(1L, BASE_DATE, 0, 0, 10);               // score 6.0
        seed(3L, BASE_DATE.minusDays(20), 0, 0, 100); // score 60.0 (월간 창 안)
        jobLauncherTestUtils.setJob(job);

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(
            new JobParametersBuilder(jobLauncherTestUtils.getUniqueJobParameters()).addString("baseDate", BASE_DATE.toString()).toJobParameters()
        );

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        // 주간은 product 3 제외(1건), 월간은 둘 다 포함하고 판매 100인 product 3이 1위
        assertThat(weeklyRepository.findAll()).hasSize(1);
        List<MonthlyProductRankModel> monthly = monthlyRepository.findAll().stream()
            .sorted(Comparator.comparingInt(ProductRankSnapshotModel::getRankNo))
            .toList();
        assertThat(monthly).hasSize(2);
        assertThat(monthly.get(0).getProductId()).isEqualTo(3L);
        assertThat(monthly.get(0).getScore()).isEqualTo(60.0);
        assertThat(monthly.get(0).getPeriodStart()).isEqualTo(BASE_DATE.minusDays(29));
    }

    @DisplayName("같은 baseDate로 재실행하면 그 기간 스냅샷만 비우고 새로 적재한다 (기간별 교체 멱등).")
    @Test
    void replacesSnapshotOnRerun() throws Exception {
        // arrange & act — 1차 실행
        seed(1L, BASE_DATE, 0, 0, 10);
        jobLauncherTestUtils.setJob(job);
        jobLauncherTestUtils.launchJob(
            new JobParametersBuilder(jobLauncherTestUtils.getUniqueJobParameters()).addString("baseDate", BASE_DATE.toString()).toJobParameters()
        );
        assertThat(weeklyRepository.findAll()).hasSize(1);

        // 데이터 교체 후 2차 실행 (다른 run — RunIdIncrementer로 파라미터 유일성 확보)
        jdbcTemplate.update("DELETE FROM product_metrics_daily");
        seed(2L, BASE_DATE, 0, 0, 5);
        seed(3L, BASE_DATE, 0, 0, 3);
        jobLauncherTestUtils.launchJob(
            new JobParametersBuilder(jobLauncherTestUtils.getUniqueJobParameters()).addString("baseDate", BASE_DATE.toString()).toJobParameters()
        );

        // assert — 1차의 product 1은 사라지고 2차 결과만 남는다
        List<WeeklyProductRankModel> weekly = weeklyRepository.findAll();
        assertThat(weekly).hasSize(2);
        assertThat(weekly).extracting(WeeklyProductRankModel::getProductId).containsExactlyInAnyOrder(2L, 3L);
    }

    @DisplayName("서로 다른 baseDate로 실행하면 각 기간 스냅샷이 함께 보존된다 (히스토리).")
    @Test
    void keepsHistoryAcrossDifferentBaseDates() throws Exception {
        // arrange — 두 개의 겹치지 않는 주간 창에 각각 상품을 시드 (주 1회 실행 전제)
        LocalDate priorBase = BASE_DATE.minusDays(7);         // 이전 주간 창 [BASE-13, BASE-7]
        seed(1L, BASE_DATE, 0, 0, 10);                        // 최신 창 [BASE-6, BASE]
        seed(2L, BASE_DATE.minusDays(10), 0, 0, 5);           // 이전 창 안 (BASE-10)
        jobLauncherTestUtils.setJob(job);

        // act — 이전 주 기준일로 1회, 최신 기준일로 1회 (전체 삭제가 아니라 각 기간만 교체)
        jobLauncherTestUtils.launchJob(
            new JobParametersBuilder(jobLauncherTestUtils.getUniqueJobParameters()).addString("baseDate", priorBase.toString()).toJobParameters()
        );
        jobLauncherTestUtils.launchJob(
            new JobParametersBuilder(jobLauncherTestUtils.getUniqueJobParameters()).addString("baseDate", BASE_DATE.toString()).toJobParameters()
        );

        // assert — 두 기간 스냅샷이 공존하고, 각 창의 상품이 해당 기간에 적재된다
        List<WeeklyProductRankModel> weekly = weeklyRepository.findAll().stream()
            .sorted(Comparator.comparing(ProductRankSnapshotModel::getPeriodEnd))
            .toList();
        assertThat(weekly).hasSize(2);

        assertThat(weekly.get(0).getPeriodStart()).isEqualTo(priorBase.minusDays(6));
        assertThat(weekly.get(0).getPeriodEnd()).isEqualTo(priorBase);
        assertThat(weekly.get(0).getProductId()).isEqualTo(2L);

        assertThat(weekly.get(1).getPeriodStart()).isEqualTo(BASE_DATE.minusDays(6));
        assertThat(weekly.get(1).getPeriodEnd()).isEqualTo(BASE_DATE);
        assertThat(weekly.get(1).getProductId()).isEqualTo(1L);
    }
}
