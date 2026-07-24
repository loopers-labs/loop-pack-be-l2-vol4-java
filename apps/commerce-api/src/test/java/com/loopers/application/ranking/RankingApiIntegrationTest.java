package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 조회 유스케이스 통합 테스트 — ZSET 순서 재정렬, 페이지네이션, Top-100 상한, 삭제 상품 스킵.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class RankingApiIntegrationTest {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired private RankingApplicationService rankingApplicationService;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private String allKey() {
        return "ranking:all:" + today.format(DAY_FMT);
    }

    private ProductModel givenProduct(String name) {
        ProductModel product = productRepository.save(new ProductModel(1L, name, "설명", 10_000L));
        stockRepository.save(StockModel.of(product.getId(), 10));
        return product;
    }

    private void rank(Long productId, double score) {
        redisTemplate.opsForZSet().add(allKey(), String.valueOf(productId), score);
    }

    @DisplayName("ZSET 점수 내림차순으로 재정렬되고 절대 순위(1-indexed)가 매겨진다")
    @Test
    void returnsInScoreDescOrderWithRanks() {
        ProductModel a = givenProduct("A");
        ProductModel b = givenProduct("B");
        ProductModel c = givenProduct("C");
        rank(a.getId(), 10.0);
        rank(b.getId(), 30.0);
        rank(c.getId(), 20.0);

        List<RankingInfo> result = rankingApplicationService.getRanking(today, 1, 20);

        assertThat(result).extracting(RankingInfo::rank).containsExactly(1, 2, 3);
        assertThat(result).extracting(r -> r.product().id()).containsExactly(b.getId(), c.getId(), a.getId());
    }

    @DisplayName("페이지네이션 — page/size 로 구간을 자르고 순위 번호는 전체 기준을 유지한다")
    @Test
    void paginatesWithAbsoluteRanks() {
        ProductModel a = givenProduct("A");
        ProductModel b = givenProduct("B");
        ProductModel c = givenProduct("C");
        rank(a.getId(), 30.0);
        rank(b.getId(), 20.0);
        rank(c.getId(), 10.0);

        List<RankingInfo> page2 = rankingApplicationService.getRanking(today, 2, 2);

        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).rank()).isEqualTo(3);
        assertThat(page2.get(0).product().id()).isEqualTo(c.getId());
    }

    @DisplayName("Top-100 밖 구간(page*size 시작이 100 이상)은 빈 목록을 반환한다")
    @Test
    void returnsEmptyBeyondTop100() {
        ProductModel a = givenProduct("A");
        rank(a.getId(), 10.0);

        // size=20, page=6 → start index = 100 (Top-100 밖)
        List<RankingInfo> result = rankingApplicationService.getRanking(today, 6, 20);

        assertThat(result).isEmpty();
    }

    @DisplayName("ZSET 에 있으나 DB 에서 사라진(삭제) 상품은 건너뛴다")
    @Test
    void skipsDeletedProducts() {
        ProductModel a = givenProduct("A");
        rank(a.getId(), 30.0);
        rank(999_999L, 20.0);   // DB 에 없는 상품

        List<RankingInfo> result = rankingApplicationService.getRanking(today, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).product().id()).isEqualTo(a.getId());
        assertThat(result.get(0).rank()).isEqualTo(1);
    }

    @DisplayName("랭킹 키가 비어 있으면 빈 목록을 반환한다")
    @Test
    void returnsEmptyWhenNoRanking() {
        assertThat(rankingApplicationService.getRanking(today, 1, 20)).isEmpty();
    }

    @DisplayName("page 가 1 미만이면 음수 인덱스로 새지 않고 빈 목록을 반환한다")
    @Test
    void returnsEmptyForInvalidPage() {
        ProductModel a = givenProduct("A");
        rank(a.getId(), 10.0);

        assertThat(rankingApplicationService.getRanking(today, 0, 20)).isEmpty();
        assertThat(rankingApplicationService.getRanking(today, -1, 20)).isEmpty();
        assertThat(rankingApplicationService.getRanking(today, 1, 0)).isEmpty();
    }

    @DisplayName("캐시 미스로 조립한 상품 정보는 상세 캐시(product:detail)를 오염시키지 않는다")
    @Test
    void doesNotPolluteSharedDetailCacheOnMiss() {
        ProductModel a = givenProduct("A");
        rank(a.getId(), 10.0);

        rankingApplicationService.getRanking(today, 1, 20);

        assertThat(redisTemplate.opsForValue().get("product:detail:" + a.getId())).isNull();
    }

    @DisplayName("일자가 바뀌어도(오늘 조회 중) 어제 날짜를 명시하면 어제 랭킹이 정상 조회된다")
    @Test
    void queriesYesterdayRankingAfterDateChange() {
        LocalDate yesterday = today.minusDays(1);
        ProductModel a = productRepository.save(new ProductModel(1L, "어제상품A", "설명", 10_000L));
        stockRepository.save(StockModel.of(a.getId(), 10));
        String yesterdayKey = "ranking:all:" + yesterday.format(DAY_FMT);
        redisTemplate.opsForZSet().add(yesterdayKey, String.valueOf(a.getId()), 15.0);
        // 오늘 키는 비어있어도(콜드스타트 이월 전 상황을 가정) 어제 조회엔 영향 없다.

        List<RankingInfo> yesterdayResult = rankingApplicationService.getRanking(yesterday, 1, 20);
        List<RankingInfo> todayResult = rankingApplicationService.getRanking(today, 1, 20);

        assertThat(yesterdayResult).hasSize(1);
        assertThat(yesterdayResult.get(0).product().id()).isEqualTo(a.getId());
        assertThat(todayResult).isEmpty();
    }

    @DisplayName("시간단위 랭킹은 ranking:hour 키를 조회해 별도로 동작한다")
    @Test
    void getsHourlyRanking() {
        LocalDateTime now = LocalDateTime.now();
        String hourKey = "ranking:hour:" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        ProductModel a = givenProduct("A");
        redisTemplate.opsForZSet().add(hourKey, String.valueOf(a.getId()), 5.0);

        List<RankingInfo> result = rankingApplicationService.getHourlyRanking(now, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).product().id()).isEqualTo(a.getId());
        assertThat(result.get(0).rank()).isEqualTo(1);
    }

    @DisplayName("weekly 랭킹은 MV 테이블의 순서를 조회하고 기존 상품 조립 정책을 유지한다")
    @Test
    void getsWeeklyRankingFromMaterializedView() {
        ProductModel a = givenProduct("A");
        ProductModel b = givenProduct("B");
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        givenWeeklyRank(weekStart, weekEnd, 1, b.getId(), 10.0);
        givenWeeklyRank(weekStart, weekEnd, 2, a.getId(), 5.0);

        List<RankingInfo> result = rankingApplicationService.getRanking(today, "weekly", 1, 20);

        assertThat(result).extracting(RankingInfo::rank).containsExactly(1, 2);
        assertThat(result).extracting(r -> r.product().id()).containsExactly(b.getId(), a.getId());
    }

    @DisplayName("monthly 랭킹은 MV 테이블에서 조회하며 Top-100 밖 페이지는 빈 목록을 반환한다")
    @Test
    void getsMonthlyRankingFromMaterializedViewWithTop100Limit() {
        ProductModel a = givenProduct("A");
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        givenMonthlyRank(monthStart, monthEnd, 1, a.getId(), 10.0);

        assertThat(rankingApplicationService.getRanking(today, "monthly", 6, 20)).isEmpty();
        assertThat(rankingApplicationService.getRanking(today, "monthly", 1, 20))
            .extracting(r -> r.product().id())
            .containsExactly(a.getId());
    }

    private void givenWeeklyRank(LocalDate start, LocalDate end, int rank, Long productId, double score) {
        jdbcTemplate.update(
            "INSERT INTO mv_product_rank_weekly "
                + "(period_start_date, period_end_date, rank_no, product_id, score, "
                + "view_count, like_count, sale_count, order_score, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 0, 0, 0, 0, NOW(6), NOW(6))",
            start, end, rank, productId, score);
    }

    private void givenMonthlyRank(LocalDate start, LocalDate end, int rank, Long productId, double score) {
        jdbcTemplate.update(
            "INSERT INTO mv_product_rank_monthly "
                + "(period_start_date, period_end_date, rank_no, product_id, score, "
                + "view_count, like_count, sale_count, order_score, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 0, 0, 0, 0, NOW(6), NOW(6))",
            start, end, rank, productId, score);
    }
}
