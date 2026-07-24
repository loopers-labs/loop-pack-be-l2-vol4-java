package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingMvPeriod;
import com.loopers.domain.ranking.RankingMvReadRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class RankingMvReadRepositoryImplIntegrationTest {

    @Autowired private RankingMvReadRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void insertWeeklyRow(String periodKey, Long productId, int rank, double score) {
        jdbcTemplate.update(
            """
                INSERT INTO mv_product_rank_weekly (period_key, product_id, rank_position, score, updated_at)
                VALUES (?, ?, ?, ?, NOW())
                """,
            periodKey, productId, rank, score
        );
    }

    private void insertMonthlyRow(String periodKey, Long productId, int rank, double score) {
        jdbcTemplate.update(
            """
                INSERT INTO mv_product_rank_monthly (period_key, product_id, rank_position, score, updated_at)
                VALUES (?, ?, ?, ?, NOW())
                """,
            periodKey, productId, rank, score
        );
    }

    @DisplayName("findPage()를 실행할 때,")
    @Nested
    class FindPage {

        @DisplayName("WEEKLY 랭킹을 rank 오름차순으로 페이지 조회한다.")
        @Test
        void returnsWeeklyPageOrderedByRank() {
            insertWeeklyRow("2026W29", 300L, 3, 10.0);
            insertWeeklyRow("2026W29", 100L, 1, 30.0);
            insertWeeklyRow("2026W29", 200L, 2, 20.0);

            List<RankingItem> page = repository.findPage(RankingMvPeriod.WEEKLY, "2026W29", 0, 2);

            assertThat(page).extracting(RankingItem::productId).containsExactly(100L, 200L);
        }

        @DisplayName("두 번째 페이지를 요청하면 이어지는 순위를 반환한다.")
        @Test
        void returnsSecondPage() {
            insertWeeklyRow("2026W29", 100L, 1, 30.0);
            insertWeeklyRow("2026W29", 200L, 2, 20.0);
            insertWeeklyRow("2026W29", 300L, 3, 10.0);

            List<RankingItem> page = repository.findPage(RankingMvPeriod.WEEKLY, "2026W29", 1, 2);

            assertThat(page).extracting(RankingItem::productId).containsExactly(300L);
        }

        @DisplayName("MONTHLY 랭킹도 동일하게 조회한다.")
        @Test
        void returnsMonthlyPage() {
            insertMonthlyRow("202607", 500L, 1, 99.0);

            List<RankingItem> page = repository.findPage(RankingMvPeriod.MONTHLY, "202607", 0, 10);

            assertThat(page).extracting(RankingItem::productId).containsExactly(500L);
        }

        @DisplayName("다른 periodKey의 데이터는 섞이지 않는다.")
        @Test
        void doesNotMixOtherPeriodKeys() {
            insertWeeklyRow("2026W29", 100L, 1, 30.0);
            insertWeeklyRow("2026W30", 999L, 1, 999.0);

            List<RankingItem> page = repository.findPage(RankingMvPeriod.WEEKLY, "2026W29", 0, 10);

            assertThat(page).extracting(RankingItem::productId).containsExactly(100L);
        }
    }

    @DisplayName("findRank()를 실행할 때,")
    @Nested
    class FindRank {

        @DisplayName("존재하는 상품이면 순위를 반환한다.")
        @Test
        void returnsRank_whenProductExists() {
            insertWeeklyRow("2026W29", 100L, 7, 30.0);

            Optional<Long> rank = repository.findRank(RankingMvPeriod.WEEKLY, "2026W29", 100L);

            assertThat(rank).contains(7L);
        }

        @DisplayName("순위권 밖(랭킹에 없는) 상품이면 빈 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenProductNotRanked() {
            Optional<Long> rank = repository.findRank(RankingMvPeriod.WEEKLY, "2026W29", 999L);

            assertThat(rank).isEmpty();
        }
    }

    @DisplayName("countTotal()을 실행할 때,")
    @Nested
    class CountTotal {

        @DisplayName("해당 periodKey의 전체 랭킹 개수를 반환한다.")
        @Test
        void returnsCount() {
            insertWeeklyRow("2026W29", 100L, 1, 30.0);
            insertWeeklyRow("2026W29", 200L, 2, 20.0);
            insertWeeklyRow("2026W30", 300L, 1, 10.0);

            long count = repository.countTotal(RankingMvPeriod.WEEKLY, "2026W29");

            assertThat(count).isEqualTo(2L);
        }
    }
}
