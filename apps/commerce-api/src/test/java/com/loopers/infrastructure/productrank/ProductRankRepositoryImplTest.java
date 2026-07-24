package com.loopers.infrastructure.productrank;

import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@DisplayName("ProductRankRepositoryImpl 통합 테스트")
class ProductRankRepositoryImplTest {

    @Autowired
    private ProductRankRepositoryImpl productRankRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("[ECP] WEEKLY 기간의 as_of_date 기준 score 내림차순으로 TOP N을 조회하고, 순위는 페이지 오프셋 기준으로 매겨진다.")
    @Test
    void findsTopNOrderedByScoreDesc_forWeeklyPeriod() {
        // arrange
        LocalDate asOfDate = LocalDate.of(2026, 7, 23);
        insertWeeklyMv(asOfDate, "PRD_LOW", 1.0);
        insertWeeklyMv(asOfDate, "PRD_HIGH", 9.0);
        insertWeeklyMv(asOfDate, "PRD_MID", 5.0);

        // act
        List<RankingItem> page = productRankRepository.findTopN(RankingPeriod.WEEKLY, asOfDate, 2, 0);

        // assert
        assertAll(
                () -> assertThat(page).hasSize(2),
                () -> assertThat(page.get(0).productId()).isEqualTo("PRD_HIGH"),
                () -> assertThat(page.get(0).rank()).isEqualTo(1L),
                () -> assertThat(page.get(1).productId()).isEqualTo("PRD_MID"),
                () -> assertThat(page.get(1).rank()).isEqualTo(2L)
        );
    }

    @DisplayName("[ECP] offset을 주면 그만큼 순위가 밀려서 매겨진다.")
    @Test
    void appliesOffsetToRank_whenPaginating() {
        // arrange
        LocalDate asOfDate = LocalDate.of(2026, 7, 23);
        insertWeeklyMv(asOfDate, "PRD_LOW", 1.0);
        insertWeeklyMv(asOfDate, "PRD_HIGH", 9.0);
        insertWeeklyMv(asOfDate, "PRD_MID", 5.0);

        // act
        List<RankingItem> page = productRankRepository.findTopN(RankingPeriod.WEEKLY, asOfDate, 2, 1);

        // assert
        assertAll(
                () -> assertThat(page).hasSize(2),
                () -> assertThat(page.get(0).productId()).isEqualTo("PRD_MID"),
                () -> assertThat(page.get(0).rank()).isEqualTo(2L),
                () -> assertThat(page.get(1).productId()).isEqualTo("PRD_LOW"),
                () -> assertThat(page.get(1).rank()).isEqualTo(3L)
        );
    }

    @DisplayName("[BVA] score가 동점이면 product_id 오름차순으로 순위가 결정된다.")
    @Test
    void breaksTieByProductIdAscending_whenScoresAreEqual() {
        // arrange
        LocalDate asOfDate = LocalDate.of(2026, 7, 23);
        insertWeeklyMv(asOfDate, "PRD_B", 5.0);
        insertWeeklyMv(asOfDate, "PRD_A", 5.0);
        insertWeeklyMv(asOfDate, "PRD_C", 5.0);

        // act
        List<RankingItem> page = productRankRepository.findTopN(RankingPeriod.WEEKLY, asOfDate, 10, 0);

        // assert
        assertThat(page).extracting(RankingItem::productId).containsExactly("PRD_A", "PRD_B", "PRD_C");
    }

    @DisplayName("[ECP] MONTHLY 기간은 mv_product_rank_monthly 테이블에서 별도로 조회된다.")
    @Test
    void queriesMonthlyTable_forMonthlyPeriod() {
        // arrange
        LocalDate asOfDate = LocalDate.of(2026, 7, 23);
        insertMonthlyMv(asOfDate, "PRD_MONTHLY", 3.0);
        insertWeeklyMv(asOfDate, "PRD_WEEKLY", 3.0);

        // act
        List<RankingItem> page = productRankRepository.findTopN(RankingPeriod.MONTHLY, asOfDate, 10, 0);

        // assert
        assertThat(page).extracting(RankingItem::productId).containsExactly("PRD_MONTHLY");
    }

    @DisplayName("[ECP] countByAsOfDate는 해당 as_of_date의 전체 상품 수를 반환한다.")
    @Test
    void countsAllProducts_forGivenAsOfDate() {
        // arrange
        LocalDate asOfDate = LocalDate.of(2026, 7, 23);
        insertWeeklyMv(asOfDate, "PRD_01", 1.0);
        insertWeeklyMv(asOfDate, "PRD_02", 2.0);
        insertWeeklyMv(asOfDate.minusDays(1), "PRD_03", 3.0); // 다른 날짜, 카운트 제외

        // act
        long count = productRankRepository.countByAsOfDate(RankingPeriod.WEEKLY, asOfDate);

        // assert
        assertThat(count).isEqualTo(2L);
    }

    @DisplayName("[BVA] DAILY 기간은 RDB MV 대상이 아니므로 지원하지 않는다.")
    @Test
    void throwsException_whenPeriodIsDaily() {
        assertThatThrownBy(() -> productRankRepository.findTopN(RankingPeriod.DAILY, LocalDate.now(), 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void insertWeeklyMv(LocalDate asOfDate, String productId, double score) {
        insertMv("mv_product_rank_weekly", asOfDate, productId, score);
    }

    private void insertMonthlyMv(LocalDate asOfDate, String productId, double score) {
        insertMv("mv_product_rank_monthly", asOfDate, productId, score);
    }

    private void insertMv(String table, LocalDate asOfDate, String productId, double score) {
        jdbcTemplate.update("""
                INSERT INTO %s (as_of_date, product_id, score, view_sum, like_delta_sum, purchase_quantity_sum, created_at)
                VALUES (?, ?, ?, 0, 0, 0, ?)
                """.formatted(table), asOfDate, productId, score, ZonedDateTime.now());
    }
}
