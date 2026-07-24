package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Sql(scripts = "/mv-product-rank-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RankingFacadeTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);

    @Autowired RankingFacade rankingFacade;
    @Autowired ProductRepository productRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @Autowired RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        // MV 는 엔티티가 없어 DatabaseCleanUp 대상이 아니므로 직접 비운다
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("랭킹 페이지는 점수 내림차순 + 상품정보(name/price) aggregation 으로 반환된다.")
    @Test
    void rankingAggregatesProductInfo() {
        Product p1 = productRepository.save(new Product(1L, "인기상품", "d", 5000L, 10));
        Product p2 = productRepository.save(new Product(1L, "차순위상품", "d", 3000L, 10));
        String key = RankingKeys.daily(DATE);
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 2.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 1.0);

        RankingPageInfo info = rankingFacade.getRankings("DAILY", "20260715", 1, 20);

        assertThat(info.period()).isEqualTo("DAILY");
        assertThat(info.periodKey()).isEqualTo("20260715");
        assertThat(info.totalCount()).isEqualTo(2L);
        assertThat(info.items()).hasSize(2);
        assertThat(info.items().get(0).rank()).isEqualTo(1L);
        assertThat(info.items().get(0).productId()).isEqualTo(p1.getId());
        assertThat(info.items().get(0).name()).isEqualTo("인기상품");
        assertThat(info.items().get(0).price()).isEqualTo(5000L);
        assertThat(info.items().get(1).rank()).isEqualTo(2L);
    }

    @DisplayName("2페이지의 rank 는 offset 이 반영된다 (size=1, page=2 → rank 2).")
    @Test
    void secondPageRankHasOffset() {
        Product p1 = productRepository.save(new Product(1L, "일등", "d", 1000L, 10));
        Product p2 = productRepository.save(new Product(1L, "이등", "d", 1000L, 10));
        String key = RankingKeys.daily(DATE);
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 2.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 1.0);

        RankingPageInfo info = rankingFacade.getRankings("DAILY", "20260715", 2, 1);

        assertThat(info.items()).hasSize(1);
        assertThat(info.items().get(0).rank()).isEqualTo(2L);
        assertThat(info.items().get(0).productId()).isEqualTo(p2.getId());
    }

    @DisplayName("DB 에 없는(삭제된) 상품은 목록에서 제외된다.")
    @Test
    void missingProductFiltered() {
        Product p1 = productRepository.save(new Product(1L, "실존상품", "d", 1000L, 10));
        String key = RankingKeys.daily(DATE);
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 2.0);
        redisTemplate.opsForZSet().add(key, "99999", 9.0); // DB 에 없는 상품이 1위

        RankingPageInfo info = rankingFacade.getRankings("DAILY", "20260715", 1, 20);

        assertThat(info.items()).hasSize(1);
        assertThat(info.items().get(0).productId()).isEqualTo(p1.getId());
    }

    @DisplayName("period 미지정이면 일간으로 동작한다(기존 API 호환).")
    @Test
    void defaultPeriodIsDaily() {
        Product p1 = productRepository.save(new Product(1L, "일간상품", "d", 1000L, 10));
        redisTemplate.opsForZSet().add(RankingKeys.daily(DATE), String.valueOf(p1.getId()), 2.0);

        RankingPageInfo info = rankingFacade.getRankings(null, "20260715", 1, 20);

        assertThat(info.period()).isEqualTo("DAILY");
        assertThat(info.items()).extracting(RankingItemInfo::productId).containsExactly(p1.getId());
    }

    @DisplayName("date 미지정이면 오늘(KST) 랭킹을 조회한다 — 빈 랭킹이면 빈 목록.")
    @Test
    void defaultDateIsToday() {
        RankingPageInfo info = rankingFacade.getRankings("DAILY", null, 1, 20);

        assertThat(info.items()).isEmpty();
        assertThat(info.totalCount()).isZero();
    }

    @DisplayName("주간은 배치가 적재한 MV 를 확정 순위대로 읽는다(실시간 ZSET 을 보지 않는다).")
    @Test
    void weeklyReadsMaterializedView() {
        Product p1 = productRepository.save(new Product(1L, "주간일등", "d", 5000L, 10));
        Product p2 = productRepository.save(new Product(1L, "주간이등", "d", 3000L, 10));
        insertWeekly("2026-W29", p1.getId(), 1, 12.5);
        insertWeekly("2026-W29", p2.getId(), 2, 7.0);
        // 같은 상품의 실시간 일간 점수는 순서가 반대 — 주간 조회는 이걸 보면 안 된다
        redisTemplate.opsForZSet().add(RankingKeys.daily(DATE), String.valueOf(p2.getId()), 99.0);

        RankingPageInfo info = rankingFacade.getRankings("WEEKLY", "20260715", 1, 20);

        assertThat(info.period()).isEqualTo("WEEKLY");
        assertThat(info.periodKey()).isEqualTo("2026-W29");
        assertThat(info.totalCount()).isEqualTo(2L);
        assertThat(info.items()).extracting(RankingItemInfo::productId).containsExactly(p1.getId(), p2.getId());
        assertThat(info.items().get(0).score()).isEqualTo(12.5);
    }

    @DisplayName("다른 기간 키의 MV 로우는 섞이지 않는다.")
    @Test
    void weeklyIsolatesOtherPeriodKeys() {
        Product p1 = productRepository.save(new Product(1L, "이번주", "d", 1000L, 10));
        Product p2 = productRepository.save(new Product(1L, "지난주", "d", 1000L, 10));
        insertWeekly("2026-W29", p1.getId(), 1, 5.0);
        insertWeekly("2026-W28", p2.getId(), 1, 9.0);

        RankingPageInfo info = rankingFacade.getRankings("WEEKLY", "20260715", 1, 20);

        assertThat(info.items()).extracting(RankingItemInfo::productId).containsExactly(p1.getId());
    }

    @DisplayName("월간은 월간 MV 를 읽는다.")
    @Test
    void monthlyReadsMonthlyView() {
        Product p1 = productRepository.save(new Product(1L, "월간일등", "d", 1000L, 10));
        jdbcTemplate.update("""
            INSERT INTO mv_product_rank_monthly
              (period_key, product_id, rank_no, score, like_count, order_count, view_count, created_at, updated_at)
            VALUES ('2026-07', ?, 1, 42.0, 0, 0, 0, NOW(), NOW())
            """, p1.getId());

        RankingPageInfo info = rankingFacade.getRankings("MONTHLY", "20260715", 1, 20);

        assertThat(info.periodKey()).isEqualTo("2026-07");
        assertThat(info.items()).extracting(RankingItemInfo::productId).containsExactly(p1.getId());
    }

    @DisplayName("적재된 적 없는 기간이면 빈 목록이다(예외 아님).")
    @Test
    void emptyPeriodReturnsEmptyPage() {
        RankingPageInfo info = rankingFacade.getRankings("WEEKLY", "20200101", 1, 20);

        assertThat(info.items()).isEmpty();
        assertThat(info.totalCount()).isZero();
    }

    @DisplayName("잘못된 date 형식·period·page<1·size<1 은 BAD_REQUEST 다.")
    @Test
    void invalidParamsRejected() {
        assertThatThrownBy(() -> rankingFacade.getRankings("DAILY", "2026-07-15", 1, 20))
            .isInstanceOf(CoreException.class)
            .extracting(e -> ((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThatThrownBy(() -> rankingFacade.getRankings("YEARLY", "20260715", 1, 20))
            .isInstanceOf(CoreException.class)
            .extracting(e -> ((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThatThrownBy(() -> rankingFacade.getRankings("DAILY", "20260715", 0, 20))
            .isInstanceOf(CoreException.class);
        assertThatThrownBy(() -> rankingFacade.getRankings("DAILY", "20260715", 1, 0))
            .isInstanceOf(CoreException.class);
    }

    private void insertWeekly(String periodKey, Long productId, int rankNo, double score) {
        jdbcTemplate.update("""
            INSERT INTO mv_product_rank_weekly
              (period_key, product_id, rank_no, score, like_count, order_count, view_count, created_at, updated_at)
            VALUES (?, ?, ?, ?, 0, 0, 0, NOW(), NOW())
            """, periodKey, productId, rankNo, score);
    }
}
