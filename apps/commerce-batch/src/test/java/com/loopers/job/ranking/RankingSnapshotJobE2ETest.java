package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.RankingSnapshotJobConfig;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 스냅샷 배치 E2E — 실제 Redis ZSET 을 심고 잡을 돌려 DB 적재를 검증한다.
 *
 * <p>commerce-batch 는 JPA 엔티티가 없으므로(스키마는 commerce-api 의 RankingSnapshotEntity 소유)
 * 테스트가 필요한 테이블을 직접 만든다 — commerce-streamer 의 ProductMetricsAggregationIntegrationTest 와 동일한 방식.
 *
 * <p>⚠️ Testcontainers(MySQL + Redis)가 필요하므로 Docker 가 떠 있어야 실행된다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + RankingSnapshotJobConfig.JOB_NAME)
class RankingSnapshotJobE2ETest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * Spring Batch 는 <b>식별 파라미터가 같으면 같은 job instance</b>로 보고 재실행을 거부한다
     * (JobInstanceAlreadyCompleteException). 이 테스트는 일부러 같은 snapshotDate 를 여러 번 돌리므로
     * (멱등·덮어쓰기 검증) 매 실행에 유니크한 run 값을 붙여 별개 instance 로 만든다.
     *
     * <p>JUnit 이 테스트마다 인스턴스를 새로 만들지만 배치 메타데이터는 컨텍스트에 남으므로 <b>static</b> 이어야 한다.
     */
    private static final AtomicLong RUN_ID = new AtomicLong();

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(RankingSnapshotJobConfig.JOB_NAME)
    private Job job;

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ranking_daily_snapshot (
                    ranking_date DATE        NOT NULL,
                    product_id   BIGINT      NOT NULL,
                    rank_no      INT         NOT NULL,
                    score        DOUBLE      NOT NULL,
                    created_at   DATETIME(6) NOT NULL,
                    PRIMARY KEY (ranking_date, product_id)
                )
                """);
        jdbcTemplate.update("DELETE FROM ranking_daily_snapshot");
        redisTemplate.delete(redisTemplate.keys("ranking:all:*"));
        jobLauncherTestUtils.setJob(job);
    }

    private void seed(LocalDate date, long productId, double score) {
        redisTemplate.opsForZSet().add("ranking:all:" + date.format(YYYYMMDD), String.valueOf(productId), score);
    }

    private List<Map<String, Object>> snapshotOf(LocalDate date) {
        return jdbcTemplate.queryForList(
                "SELECT product_id, rank_no, score FROM ranking_daily_snapshot WHERE ranking_date = ? ORDER BY rank_no",
                date);
    }

    private ExitStatus run(LocalDate date) throws Exception {
        return jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("snapshotDate", date.format(YYYYMMDD))
                .addLong("run", RUN_ID.incrementAndGet()) // 같은 날짜 재실행을 위한 유니크 식별자
                .toJobParameters()).getExitStatus();
    }

    /** snapshotDate 를 주지 않고 실행 — 기본값(어제) 경로 검증용. */
    private ExitStatus runWithoutDate() throws Exception {
        return jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLong("run", RUN_ID.incrementAndGet())
                .toJobParameters()).getExitStatus();
    }

    @DisplayName("ZSET 상위 랭킹이 순위·스코어와 함께 스냅샷 테이블로 적재된다.")
    @Test
    void snapshotsRanking() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 14);
        seed(date, 30L, 9.0);
        seed(date, 10L, 5.0);
        seed(date, 20L, 3.0);

        ExitStatus status = run(date);

        assertThat(status.getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        List<Map<String, Object>> rows = snapshotOf(date);
        assertThat(rows).hasSize(3);
        // 점수 내림차순 → 1위 30, 2위 10, 3위 20
        assertThat(rows.get(0)).containsEntry("product_id", 30L).containsEntry("rank_no", 1);
        assertThat(rows.get(1)).containsEntry("product_id", 10L).containsEntry("rank_no", 2);
        assertThat(rows.get(2)).containsEntry("product_id", 20L).containsEntry("rank_no", 3);
        assertThat((Double) rows.get(0).get("score")).isEqualTo(9.0);
    }

    @DisplayName("같은 날짜로 다시 돌려도 중복 없이 같은 상태가 된다(재실행 안전).")
    @Test
    void isIdempotent() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 14);
        seed(date, 30L, 9.0);
        seed(date, 10L, 5.0);

        run(date);
        List<Map<String, Object>> first = snapshotOf(date);
        run(date);
        List<Map<String, Object>> second = snapshotOf(date);

        assertThat(first).hasSize(2);
        assertThat(second).hasSize(2); // 4건으로 불어나지 않는다
        assertThat(second).isEqualTo(first);
    }

    @DisplayName("ZSET이 갱신된 뒤 다시 돌리면 최신 순위로 덮어쓴다.")
    @Test
    void overwritesWithLatest() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 14);
        seed(date, 30L, 9.0);
        seed(date, 10L, 5.0);
        run(date);

        // 10번이 역전
        seed(date, 10L, 20.0);
        run(date);

        List<Map<String, Object>> rows = snapshotOf(date);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("product_id", 10L).containsEntry("rank_no", 1);
        assertThat(rows.get(1)).containsEntry("product_id", 30L).containsEntry("rank_no", 2);
    }

    @DisplayName("그날 ZSET이 비어 있으면 적재 없이 성공하고, 기존 스냅샷은 정리된다.")
    @Test
    void clearsWhenSourceEmpty() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 14);
        seed(date, 30L, 9.0);
        run(date);
        assertThat(snapshotOf(date)).hasSize(1);

        // ZSET 이 사라진 상태(TTL 만료 등)에서 재실행
        redisTemplate.delete("ranking:all:" + date.format(YYYYMMDD));
        ExitStatus status = run(date);

        assertThat(status.getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        assertThat(snapshotOf(date)).isEmpty();
    }

    @DisplayName("snapshotDate를 생략하면 어제(KST)를 대상으로 삼는다.")
    @Test
    void defaultsToYesterday() throws Exception {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        seed(yesterday, 77L, 4.0);

        ExitStatus status = runWithoutDate();

        assertThat(status.getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        assertThat(snapshotOf(yesterday))
                .singleElement()
                .satisfies(row -> assertThat(row).containsEntry("product_id", 77L).containsEntry("rank_no", 1));
    }
}
