package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.RankingAggregationJobConfig;
import com.loopers.domain.productmetrics.ProductMetricsHourly;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.Period;
import com.loopers.infrastructure.productmetrics.ProductMetricsHourlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 주간·월간 랭킹 집계 배치 E2E. product_metrics_hourly(시간 단위 SOT)를 기간 범위로 집계해
 * MV(TOP 100)에 점수 내림차순으로 순위를 적재하는지, 재실행이 멱등한지 검증한다.
 */
@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.batch.job.name=" + RankingAggregationJobConfig.JOB_NAME,
    // 잡은 JobLauncherTestUtils로만 구동한다 — 컨텍스트 기동 시 자동 실행되지 않도록 러너를 끈다.
    "spring.batch.job.enabled=false",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
class RankingAggregationJobE2ETest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-07-15(수) → ISO주 월2026-07-13 ~ 일2026-07-19, 달 2026-07.
    private static final LocalDate BASE = LocalDate.of(2026, 7, 15);

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(RankingAggregationJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductMetricsHourlyJpaRepository hourlyRepository;

    @Autowired
    private MvProductRankWeeklyJpaRepository weeklyRepository;

    @Autowired
    private MvProductRankMonthlyJpaRepository monthlyRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @BeforeEach
    void setUp() {
        hourlyRepository.deleteAll();
        weeklyRepository.deleteAll();
        monthlyRepository.deleteAll();
        jobLauncherTestUtils.setJob(job);
    }

    @AfterEach
    void tearDown() {
        hourlyRepository.deleteAll();
        weeklyRepository.deleteAll();
        monthlyRepository.deleteAll();
    }

    /** 해당 날짜 정오(KST) 버킷에 지표를 남긴다. sales는 주문 금액(order_amount)으로 적재된다. */
    private void seedHourly(long productId, LocalDate date, long like, long sales, long view) {
        ZonedDateTime bucketHour = date.atTime(12, 0).atZone(SEOUL);
        hourlyRepository.save(ProductMetricsHourly.of(productId, bucketHour, "UNKNOWN", view, like, sales));
    }

    private JobParameters params(Period period, long runId) {
        return new JobParametersBuilder()
            .addString("baseDate", "20260715")
            .addString("period", period.name())
            .addLong("run", runId)
            .toJobParameters();
    }

    @DisplayName("주간 집계 시 그 주의 시간대별 지표를 상품별로 합산해 점수 내림차순으로 순위를 매겨 적재한다")
    @Test
    void aggregatesWeeklyRankByScoreDesc() throws Exception {
        // arrange - 같은 주(월~일) 내 서로 다른 지표. 점수=0.1*view+0.2*like+0.6*sales
        seedHourly(1L, LocalDate.of(2026, 7, 13), 0, 0, 10);   // score 1.0
        seedHourly(1L, LocalDate.of(2026, 7, 14), 0, 0, 0);    // 같은 상품, 같은 주 → 합산(변화 없음)
        seedHourly(2L, LocalDate.of(2026, 7, 19), 0, 10, 0);   // score 6.0
        seedHourly(3L, LocalDate.of(2026, 7, 15), 10, 0, 0);   // score 2.0
        seedHourly(9L, LocalDate.of(2026, 7, 20), 0, 99, 0);   // 주 범위 밖(월요일 다음주) → 제외

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(params(Period.WEEKLY, 1L));
        // NOTE: run 값은 테스트마다 다르게 준다 — 같은 파라미터 조합은 Spring Batch가
        // 완료된 JobInstance로 보고 재실행을 거부(JobInstanceAlreadyComplete)하기 때문.

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        List<MvProductRankWeekly> ranks = weeklyRepository.findAll().stream()
            .sorted(Comparator.comparingInt(MvProductRankWeekly::getRankNo))
            .toList();
        assertThat(ranks).hasSize(3);
        String expectedKey = Period.WEEKLY.key(BASE);
        assertThat(ranks).allSatisfy(r -> assertThat(r.getPeriodKey()).isEqualTo(expectedKey));
        // 점수 내림차순: p2(6.0) > p3(2.0) > p1(1.0)
        assertThat(ranks.get(0).getProductId()).isEqualTo(2L);
        assertThat(ranks.get(0).getScore()).isEqualTo(6.0);
        assertThat(ranks.get(1).getProductId()).isEqualTo(3L);
        assertThat(ranks.get(2).getProductId()).isEqualTo(1L);
        assertThat(ranks.get(2).getViewCount()).isEqualTo(10L);
    }

    @DisplayName("같은 baseDate/period로 재실행하면 기존 주기를 지우고 다시 적재해 행이 중복되지 않는다")
    @Test
    void reRunIsIdempotent() throws Exception {
        // arrange
        seedHourly(1L, LocalDate.of(2026, 7, 15), 0, 5, 0);

        // act - 서로 다른 run 파라미터로 두 번 실행(같은 periodKey)
        jobLauncherTestUtils.launchJob(params(Period.WEEKLY, 10L));
        var second = jobLauncherTestUtils.launchJob(params(Period.WEEKLY, 11L));

        // assert - 6행이 아니라 1행만 남는다
        assertThat(second.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        assertThat(weeklyRepository.findAll()).hasSize(1);
    }

    @DisplayName("MV 적재 시 행마다 SELECT를 덧붙이지 않고 적재 건수만큼만 INSERT 한다")
    @Test
    void writesWithoutPerRowSelect() throws Exception {
        // arrange - 상위 3개 상품이 적재될 데이터
        seedHourly(1L, LocalDate.of(2026, 7, 13), 0, 3, 0);
        seedHourly(2L, LocalDate.of(2026, 7, 14), 0, 2, 0);
        seedHourly(3L, LocalDate.of(2026, 7, 15), 0, 1, 0);
        Statistics stats = statistics();
        stats.clear();

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(params(Period.WEEKLY, 30L));

        // assert - Writer가 merge가 아닌 persist를 쓰므로 INSERT 전 조회가 붙지 않는다.
        // (merge 경로였다면 BaseEntity의 id=0L 탓에 detached로 판정돼 행마다 SELECT가 1번씩 더 나간다)
        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(stats.getEntityInsertCount()).isEqualTo(3L),
            () -> assertThat(stats.getEntityLoadCount()).isZero()
        );
    }

    @DisplayName("월간 집계 시 그 달 전체의 시간대별 지표를 합산해 월간 MV에 적재한다")
    @Test
    void aggregatesMonthly() throws Exception {
        // arrange - 같은 달(2026-07) 서로 다른 날
        seedHourly(1L, LocalDate.of(2026, 7, 2), 0, 1, 0);
        seedHourly(1L, LocalDate.of(2026, 7, 28), 0, 2, 0);   // 같은 상품 월 합산 → 주문금액 3
        seedHourly(2L, LocalDate.of(2026, 8, 1), 0, 99, 0);   // 다음 달 → 제외

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(params(Period.MONTHLY, 20L));

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        var ranks = monthlyRepository.findAll();
        assertThat(ranks).hasSize(1);
        assertThat(ranks.get(0).getProductId()).isEqualTo(1L);
        assertThat(ranks.get(0).getOrderAmount()).isEqualTo(3L);
        assertThat(ranks.get(0).getPeriodKey()).isEqualTo(Period.MONTHLY.key(BASE));
    }
}
