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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 조회 유스케이스 통합 테스트 — ZSET 순서 재정렬, 페이지네이션, Top-100 상한, 삭제 상품 스킵.
 */
@SpringBootTest
class RankingApiIntegrationTest {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired private RankingApplicationService rankingApplicationService;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
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
}
