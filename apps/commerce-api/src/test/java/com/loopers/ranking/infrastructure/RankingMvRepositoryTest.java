package com.loopers.ranking.infrastructure;

import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingMvRepository;
import com.loopers.ranking.domain.RankingPeriod;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MV 는 batch 가 만들어 commerce-api 는 읽기만 한다 — 엔티티 없이 JDBC 로 조회한다.
 * 인덱스(period_key, score DESC, product_id ASC) 순서대로 페이지를 자르고, 순위는 조회 순번으로 매긴다.
 */
@SpringBootTest
class RankingMvRepositoryTest {

    private final RankingMvRepository rankingMvRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    RankingMvRepositoryTest(RankingMvRepository rankingMvRepository, JdbcTemplate jdbcTemplate,
                            DatabaseCleanUp databaseCleanUp) {
        this.rankingMvRepository = rankingMvRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        // MV 는 batch 소유라 commerce-api 엔 엔티티가 없다 — 조회 대상 테이블을 직접 만든다(batch DDL 과 같은 컬럼).
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
                    period_key varchar(16) NOT NULL, product_id bigint NOT NULL, score double NOT NULL,
                    view_count bigint NOT NULL, like_count bigint NOT NULL, sales_count bigint NOT NULL,
                    created_at datetime(6) NOT NULL,
                    PRIMARY KEY (product_id, period_key),
                    KEY idx_period_score (period_key, score DESC, product_id ASC))
                """);
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        databaseCleanUp.truncateAllTables();
    }

    private void row(String periodKey, long productId, double score) {
        jdbcTemplate.update("""
                INSERT INTO mv_product_rank_weekly
                    (period_key, product_id, score, view_count, like_count, sales_count, created_at)
                VALUES (?, ?, ?, 0, 0, 0, NOW())
                """, periodKey, productId, score);
    }

    @Test
    @DisplayName("score 내림차순으로 페이지를 잘라 낸다")
    void givenRows_whenFindPage_thenOrderedByScoreDesc() {
        row("2026-W30", 100L, 5.0);
        row("2026-W30", 200L, 9.0);
        row("2026-W30", 300L, 1.0);

        List<RankingEntry> page = rankingMvRepository.findPage(RankingPeriod.WEEKLY, "2026-W30", 0, 2);

        assertThat(page).extracting(RankingEntry::productId).containsExactly(200L, 100L);
    }

    @Test
    @DisplayName("동점이면 product_id 오름차순으로 순서를 고정한다")
    void givenTiedScores_whenFindPage_thenProductIdAscending() {
        row("2026-W30", 300L, 5.0);
        row("2026-W30", 100L, 5.0);
        row("2026-W30", 200L, 5.0);

        List<RankingEntry> page = rankingMvRepository.findPage(RankingPeriod.WEEKLY, "2026-W30", 0, 10);

        assertThat(page).extracting(RankingEntry::productId).containsExactly(100L, 200L, 300L);
    }

    @Test
    @DisplayName("offset 부터 limit 만큼만 낸다")
    void givenOffset_whenFindPage_thenSlice() {
        for (long id = 1; id <= 5; id++) {
            row("2026-W30", id, id);   // score = id, 내림차순이면 5,4,3,2,1
        }

        List<RankingEntry> page = rankingMvRepository.findPage(RankingPeriod.WEEKLY, "2026-W30", 2, 2);

        assertThat(page).extracting(RankingEntry::productId).containsExactly(3L, 2L);
    }

    @Test
    @DisplayName("다른 기간의 행은 섞이지 않는다")
    void givenOtherPeriod_whenFindPage_thenIsolated() {
        row("2026-W30", 100L, 5.0);
        row("2026-W29", 200L, 9.0);

        assertThat(rankingMvRepository.findPage(RankingPeriod.WEEKLY, "2026-W30", 0, 10))
                .extracting(RankingEntry::productId).containsExactly(100L);
    }

    @Test
    @DisplayName("그 기간의 전체 개수를 센다")
    void givenRows_whenCount_thenTotalForThatPeriod() {
        row("2026-W30", 100L, 5.0);
        row("2026-W30", 200L, 9.0);
        row("2026-W29", 300L, 1.0);

        assertThat(rankingMvRepository.count(RankingPeriod.WEEKLY, "2026-W30")).isEqualTo(2);
    }

    @Test
    @DisplayName("행이 없으면 빈 페이지와 0을 낸다")
    void givenNoRows_whenQueried_thenEmpty() {
        assertThat(rankingMvRepository.findPage(RankingPeriod.WEEKLY, "2026-W99", 0, 10)).isEmpty();
        assertThat(rankingMvRepository.count(RankingPeriod.WEEKLY, "2026-W99")).isZero();
    }
}
