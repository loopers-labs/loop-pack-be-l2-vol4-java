package com.loopers.application.ranking;

import com.loopers.application.support.PageResult;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.money.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.ranking.RankingKeys;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RankingFacadeIntegrationTest {

    @Autowired
    private RankingFacade rankingFacade;
    @Autowired
    private BrandJpaRepository brandJpaRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
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

    private Product saveProduct(String name) {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        return productJpaRepository.save(new Product(name, "설명",
            new Money(BigDecimal.valueOf(1000)), new Stock(10), brand.getId()));
    }

    private void seedScore(LocalDate date, long productId, double score) {
        redisTemplate.opsForZSet().add(RankingKeys.of(date), String.valueOf(productId), score);
    }

    @DisplayName("랭킹 페이지를 조회하면, ")
    @Nested
    class GetRankings {

        @DisplayName("점수 순으로 정렬된 상품 정보와 1부터 시작하는 순위를 함께 반환한다.")
        @Test
        void returnsRankedProductsWithDisplayRank() {
            // arrange
            LocalDate date = LocalDate.of(2025, 9, 6);
            Product first = saveProduct("1등 상품");
            Product second = saveProduct("2등 상품");
            seedScore(date, first.getId(), 90.0);
            seedScore(date, second.getId(), 50.0);

            // act
            PageResult<RankingInfo> result = rankingFacade.getRankings(date, 1, 20);

            // assert
            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).rank()).isEqualTo(1L);
            assertThat(result.items().get(0).product().id()).isEqualTo(first.getId());
            assertThat(result.items().get(1).rank()).isEqualTo(2L);
            assertThat(result.items().get(1).product().id()).isEqualTo(second.getId());
            assertThat(result.totalElements()).isEqualTo(2L);
        }

        @DisplayName("랭킹에는 있지만 상품이 삭제되어 DB에 없으면, 해당 항목은 결과에서 제외된다.")
        @Test
        void skipsMissingProduct_whenRankedButNotInDb() {
            // arrange
            LocalDate date = LocalDate.of(2025, 9, 6);
            Product existing = saveProduct("살아있는 상품");
            seedScore(date, existing.getId(), 90.0);
            seedScore(date, 999_999L, 50.0); // DB에는 없는 상품 ID

            // act
            PageResult<RankingInfo> result = rankingFacade.getRankings(date, 1, 20);

            // assert
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).product().id()).isEqualTo(existing.getId());
        }
    }

    @DisplayName("개별 상품의 순위를 조회하면, ")
    @Nested
    class GetRank {

        @DisplayName("1부터 시작하는 표시용 순위를 반환한다.")
        @Test
        void returnsOneIndexedRank() {
            // arrange
            LocalDate date = LocalDate.of(2025, 9, 6);
            seedScore(date, 101L, 90.0);
            seedScore(date, 102L, 50.0);

            // assert
            assertThat(rankingFacade.getRank(date, 101L)).contains(1L);
            assertThat(rankingFacade.getRank(date, 102L)).contains(2L);
        }

        @DisplayName("랭킹에 없는 상품이면, 빈 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenProductNotRanked() {
            // arrange
            LocalDate date = LocalDate.of(2025, 9, 6);

            // act
            Optional<Long> result = rankingFacade.getRank(date, 999L);

            // assert
            assertThat(result).isEmpty();
        }
    }
}
