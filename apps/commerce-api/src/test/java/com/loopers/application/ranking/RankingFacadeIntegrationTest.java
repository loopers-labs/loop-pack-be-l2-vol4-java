package com.loopers.application.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.domain.ranking.MvProductRankMonthlyModel;
import com.loopers.domain.ranking.MvProductRankWeeklyModel;
import com.loopers.domain.ranking.RankingHourlyQueryCondition;
import com.loopers.domain.ranking.RankingMvQueryCondition;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingQueryCondition;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class RankingFacadeIntegrationTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOURLY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @Autowired
    private RankingFacade rankingFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductStatsRepository productStatsRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private MvProductRankWeeklyJpaRepository mvProductRankWeeklyJpaRepository;

    @Autowired
    private MvProductRankMonthlyJpaRepository mvProductRankMonthlyJpaRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private ProductModel saveProduct(Long brandId, String name, BigDecimal price) {
        ProductModel product = productRepository.save(new ProductModel(brandId, name, price));
        productStatsRepository.save(new ProductStatsModel(product));
        stockRepository.save(new StockModel(product.getId(), 10L));
        return product;
    }

    private void seedScore(LocalDate date, Long productId, double score) {
        String key = "ranking:all:" + date.format(DATE_FORMAT);
        redisTemplate.opsForZSet().add(key, String.valueOf(productId), score);
    }

    private void seedHourlyScore(LocalDateTime dateTime, Long productId, double score) {
        String key = "ranking:hourly:" + dateTime.format(HOURLY_DATE_FORMAT);
        redisTemplate.opsForZSet().add(key, String.valueOf(productId), score);
    }

    @DisplayName("랭킹 페이지를 조회할 때,")
    @Nested
    class GetRankings {

        @DisplayName("점수 내림차순으로 순위와 상품 정보가 조합되어 반환된다.")
        @Test
        void returnsRankingsOrderedByScoreDesc_withAggregatedProductInfo() {
            // given
            LocalDate date = LocalDate.of(2026, 7, 16);
            BrandModel brand = brandRepository.save(new BrandModel("Nike"));
            ProductModel high = saveProduct(brand.getId(), "1위상품", BigDecimal.valueOf(10000));
            ProductModel low = saveProduct(brand.getId(), "2위상품", BigDecimal.valueOf(20000));
            seedScore(date, high.getId(), 100.0);
            seedScore(date, low.getId(), 50.0);

            // when
            RankingPageInfo result = rankingFacade.getRankings(new RankingQueryCondition(date, 1, 20));

            // then
            assertAll(
                    () -> assertThat(result.totalElements()).isEqualTo(2),
                    () -> assertThat(result.items()).hasSize(2),
                    () -> assertThat(result.items().get(0).rank()).isEqualTo(1L),
                    () -> assertThat(result.items().get(0).product().id()).isEqualTo(high.getId()),
                    () -> assertThat(result.items().get(0).product().name()).isEqualTo("1위상품"),
                    () -> assertThat(result.items().get(0).product().brandName()).isEqualTo("Nike"),
                    () -> assertThat(result.items().get(1).rank()).isEqualTo(2L),
                    () -> assertThat(result.items().get(1).product().id()).isEqualTo(low.getId())
            );
        }

        @DisplayName("size만큼 페이지가 나뉘어 반환된다.")
        @Test
        void paginatesResults_bySize() {
            // given
            LocalDate date = LocalDate.of(2026, 7, 16);
            BrandModel brand = brandRepository.save(new BrandModel("Adidas"));
            for (int i = 1; i <= 5; i++) {
                ProductModel product = saveProduct(brand.getId(), "상품" + i, BigDecimal.valueOf(i * 1000));
                seedScore(date, product.getId(), i);
            }

            // when
            RankingPageInfo result = rankingFacade.getRankings(new RankingQueryCondition(date, 2, 2));

            // then
            assertAll(
                    () -> assertThat(result.totalElements()).isEqualTo(5),
                    () -> assertThat(result.items()).hasSize(2),
                    () -> assertThat(result.items().get(0).rank()).isEqualTo(3L)
            );
        }

        @DisplayName("해당 날짜에 랭킹 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoRankingDataForDate() {
            // when
            RankingPageInfo result = rankingFacade.getRankings(new RankingQueryCondition(LocalDate.of(2000, 1, 1), 1, 20));

            // then
            assertAll(
                    () -> assertThat(result.items()).isEmpty(),
                    () -> assertThat(result.totalElements()).isEqualTo(0)
            );
        }
    }

    @DisplayName("시간별 랭킹 페이지를 조회할 때,")
    @Nested
    class GetHourlyRankings {

        @DisplayName("점수 내림차순으로 순위와 상품 정보가 조합되어 반환된다.")
        @Test
        void returnsRankingsOrderedByScoreDesc_withAggregatedProductInfo() {
            // given
            LocalDateTime dateTime = LocalDateTime.of(2026, 7, 16, 23, 0);
            BrandModel brand = brandRepository.save(new BrandModel("Nike"));
            ProductModel high = saveProduct(brand.getId(), "1위상품", BigDecimal.valueOf(10000));
            ProductModel low = saveProduct(brand.getId(), "2위상품", BigDecimal.valueOf(20000));
            seedHourlyScore(dateTime, high.getId(), 100.0);
            seedHourlyScore(dateTime, low.getId(), 50.0);

            // when
            RankingPageInfo result = rankingFacade.getHourlyRankings(new RankingHourlyQueryCondition(dateTime, 1, 20));

            // then
            assertAll(
                    () -> assertThat(result.totalElements()).isEqualTo(2),
                    () -> assertThat(result.items()).hasSize(2),
                    () -> assertThat(result.items().get(0).rank()).isEqualTo(1L),
                    () -> assertThat(result.items().get(0).product().id()).isEqualTo(high.getId())
            );
        }

        @DisplayName("해당 시간에 랭킹 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoRankingDataForHour() {
            // when
            RankingPageInfo result =
                    rankingFacade.getHourlyRankings(new RankingHourlyQueryCondition(LocalDateTime.of(2000, 1, 1, 0, 0), 1, 20));

            // then
            assertAll(
                    () -> assertThat(result.items()).isEmpty(),
                    () -> assertThat(result.totalElements()).isEqualTo(0)
            );
        }

        @DisplayName("일간 랭킹과 시간별 랭킹은 서로 다른 키를 조회하므로 섞이지 않는다.")
        @Test
        void doesNotMixWithDailyRanking() {
            // given
            LocalDate date = LocalDate.of(2026, 7, 16);
            LocalDateTime dateTime = LocalDateTime.of(2026, 7, 16, 23, 0);
            BrandModel brand = brandRepository.save(new BrandModel("Nike"));
            ProductModel dailyOnly = saveProduct(brand.getId(), "일간전용", BigDecimal.valueOf(10000));
            seedScore(date, dailyOnly.getId(), 100.0);

            // when
            RankingPageInfo result = rankingFacade.getHourlyRankings(new RankingHourlyQueryCondition(dateTime, 1, 20));

            // then
            assertThat(result.items()).isEmpty();
        }
    }

    @DisplayName("주간(MV) 랭킹 페이지를 조회할 때,")
    @Nested
    class GetWeeklyRankings {

        // 2024-01-03(수)이 속한 주의 월요일은 2024-01-01. MV 는 week_start_date 로 저장된다.
        private static final LocalDate WEEK_START = LocalDate.of(2024, 1, 1);

        @DisplayName("MV 의 ranking 오름차순으로 순위와 상품 정보가 조합되어 반환된다.")
        @Test
        void returnsWeeklyRankings_fromMv() {
            // given
            BrandModel brand = brandRepository.save(new BrandModel("Nike"));
            ProductModel first = saveProduct(brand.getId(), "1위상품", BigDecimal.valueOf(10000));
            ProductModel second = saveProduct(brand.getId(), "2위상품", BigDecimal.valueOf(20000));
            mvProductRankWeeklyJpaRepository.save(new MvProductRankWeeklyModel(WEEK_START, first.getId(), 100L, 1));
            mvProductRankWeeklyJpaRepository.save(new MvProductRankWeeklyModel(WEEK_START, second.getId(), 60L, 2));

            // when - date 는 주 중간 날짜여도 해당 주(월요일)로 해석된다
            RankingPageInfo result = rankingFacade.getMvRankings(
                new RankingMvQueryCondition(RankingPeriod.WEEKLY, LocalDate.of(2024, 1, 3), 1, 20));

            // then
            assertAll(
                () -> assertThat(result.totalElements()).isEqualTo(2),
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(result.items().get(0).rank()).isEqualTo(1L),
                () -> assertThat(result.items().get(0).score()).isEqualTo(100.0),
                () -> assertThat(result.items().get(0).product().id()).isEqualTo(first.getId()),
                () -> assertThat(result.items().get(1).rank()).isEqualTo(2L),
                () -> assertThat(result.items().get(1).product().id()).isEqualTo(second.getId())
            );
        }

        @DisplayName("해당 주에 MV 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoMvDataForWeek() {
            // when
            RankingPageInfo result = rankingFacade.getMvRankings(
                new RankingMvQueryCondition(RankingPeriod.WEEKLY, LocalDate.of(2000, 1, 1), 1, 20));

            // then
            assertAll(
                () -> assertThat(result.items()).isEmpty(),
                () -> assertThat(result.totalElements()).isEqualTo(0)
            );
        }
    }

    @DisplayName("월간(MV) 랭킹 페이지를 조회할 때,")
    @Nested
    class GetMonthlyRankings {

        // 2024-01-15 가 속한 달의 1일은 2024-01-01. MV 는 month_start_date 로 저장된다.
        private static final LocalDate MONTH_START = LocalDate.of(2024, 1, 1);

        @DisplayName("MV 의 ranking 오름차순으로 순위와 상품 정보가 조합되어 반환된다.")
        @Test
        void returnsMonthlyRankings_fromMv() {
            // given
            BrandModel brand = brandRepository.save(new BrandModel("Adidas"));
            ProductModel first = saveProduct(brand.getId(), "1위상품", BigDecimal.valueOf(10000));
            ProductModel second = saveProduct(brand.getId(), "2위상품", BigDecimal.valueOf(20000));
            mvProductRankMonthlyJpaRepository.save(new MvProductRankMonthlyModel(MONTH_START, first.getId(), 500L, 1));
            mvProductRankMonthlyJpaRepository.save(new MvProductRankMonthlyModel(MONTH_START, second.getId(), 300L, 2));

            // when - date 는 달 중간 날짜여도 해당 달(1일)로 해석된다
            RankingPageInfo result = rankingFacade.getMvRankings(
                new RankingMvQueryCondition(RankingPeriod.MONTHLY, LocalDate.of(2024, 1, 15), 1, 20));

            // then
            assertAll(
                () -> assertThat(result.totalElements()).isEqualTo(2),
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(result.items().get(0).rank()).isEqualTo(1L),
                () -> assertThat(result.items().get(0).score()).isEqualTo(500.0),
                () -> assertThat(result.items().get(0).product().id()).isEqualTo(first.getId()),
                () -> assertThat(result.items().get(1).rank()).isEqualTo(2L),
                () -> assertThat(result.items().get(1).product().id()).isEqualTo(second.getId())
            );
        }

        @DisplayName("같은 기준일이라도 주간 MV 는 월간 조회에 섞이지 않는다.")
        @Test
        void doesNotMixWithWeeklyMv() {
            // given - 주간 MV 만 적재
            BrandModel brand = brandRepository.save(new BrandModel("Nike"));
            ProductModel weeklyOnly = saveProduct(brand.getId(), "주간전용", BigDecimal.valueOf(10000));
            mvProductRankWeeklyJpaRepository.save(new MvProductRankWeeklyModel(MONTH_START, weeklyOnly.getId(), 100L, 1));

            // when - 월간으로 조회
            RankingPageInfo result = rankingFacade.getMvRankings(
                new RankingMvQueryCondition(RankingPeriod.MONTHLY, LocalDate.of(2024, 1, 15), 1, 20));

            // then
            assertThat(result.items()).isEmpty();
        }
    }
}
