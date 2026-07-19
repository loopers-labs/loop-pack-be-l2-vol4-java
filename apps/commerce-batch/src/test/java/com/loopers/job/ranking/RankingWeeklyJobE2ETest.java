package com.loopers.job.ranking;

import com.loopers.batch.domain.ranking.WeeklyProductRankMv;
import com.loopers.infrastructure.ranking.WeeklyProductRankMvJpaRepository;
import com.loopers.batch.job.ranking.RankingWeeklyJobConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
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
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
    "spring.batch.job.name=" + RankingWeeklyJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"   // startup 자동 실행 끄고, 테스트에서 수동 실행만 한다
})
class RankingWeeklyJobE2ETest {

    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 7, 19); // 윈도우 = [7/13, 7/19]

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    @Qualifier(RankingWeeklyJobConfig.JOB_NAME)
    private Job job;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private WeeklyProductRankMvJpaRepository mvRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        // product_metrics 는 streamer 소유라 배치 모듈엔 엔티티가 없다 → 테스트에서 직접 만든다.
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS product_metrics ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, metric_date DATE NOT NULL, "
            + "like_count BIGINT NOT NULL, sale_count BIGINT NOT NULL, view_count BIGINT NOT NULL, order_score DOUBLE NOT NULL, "
            + "UNIQUE KEY uk_product_metrics_product_date (product_id, metric_date))");
        jdbcTemplate.update("DELETE FROM product_metrics");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables(); // mv, staging (엔티티 테이블)
        jdbcTemplate.update("DELETE FROM product_metrics");
    }

    @DisplayName("주간 잡은 지난 7일 일별 집계를 상품별로 합산해 점수순 상위 랭킹을 MV에 적재한다.")
    @Test
    void aggregatesWeeklyRankingIntoMv() throws Exception {
        insertDaily(200L, SNAPSHOT, 0, 0, 5.0);              // 0.7*log 로 이미 확정된 주문점수 5.0
        insertDaily(100L, SNAPSHOT.minusDays(2), 10, 0, 0.0); // 0.1*10 = 1.0
        insertDaily(300L, SNAPSHOT.minusDays(6), 1, 0, 0.0);  // 0.1*1 = 0.1
        insertDaily(100L, SNAPSHOT.minusDays(7), 1000, 0, 0.0); // 윈도우 밖 → 제외돼야 함

        jobLauncherTestUtils.setJob(job);
        JobParameters params = new JobParametersBuilder().addLocalDate("snapshotDate", SNAPSHOT).toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        List<WeeklyProductRankMv> ranks = mvRepository.findAll().stream()
            .sorted(Comparator.comparingInt(WeeklyProductRankMv::getRank))
            .toList();
        assertThat(ranks).hasSize(3);
        assertThat(ranks.get(0).getProductId()).isEqualTo(200L);
        assertThat(ranks.get(0).getScore()).isCloseTo(5.0, within(1e-9));
        assertThat(ranks.get(1).getProductId()).isEqualTo(100L);
        assertThat(ranks.get(1).getScore()).isCloseTo(1.0, within(1e-9));
        assertThat(ranks.get(2).getProductId()).isEqualTo(300L);
        assertThat(ranks.get(2).getScore()).isCloseTo(0.1, within(1e-9));
    }

    private void insertDaily(long productId, LocalDate date, long view, long like, double orderScore) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, metric_date, like_count, sale_count, view_count, order_score) VALUES (?,?,?,?,?,?)",
            productId, date, like, 0, view, orderScore);
    }
}
