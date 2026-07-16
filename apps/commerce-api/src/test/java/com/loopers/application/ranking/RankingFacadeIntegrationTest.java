package com.loopers.application.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.domain.ranking.RankingHourlyQueryCondition;
import com.loopers.domain.ranking.RankingQueryCondition;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
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
}
