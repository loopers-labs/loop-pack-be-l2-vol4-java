package com.loopers.domain.ranking;

import com.loopers.application.ranking.RankingInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.infrastructure.ranking.MvActiveVersionJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class MvRankingServiceIntegrationTest {

    private static final long BATCH_ID   = 1_000_000L;
    private static final int  YEAR_WEEK  = 202530;
    private static final int  YEAR_MONTH = 202507;

    @Autowired private MvRankingService mvRankingService;
    @Autowired private MvProductRankWeeklyJpaRepository weeklyRepository;
    @Autowired private MvProductRankMonthlyJpaRepository monthlyRepository;
    @Autowired private MvActiveVersionJpaRepository activeVersionRepository;
    @Autowired private BrandService brandService;
    @Autowired private ProductService productService;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private CacheManager cacheManager;

    private BrandModel brand;

    @BeforeEach
    void setUp() {
        brand = brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);
    }

    @AfterEach
    void tearDown() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주간 랭킹 조회 시,")
    @Nested
    class GetWeeklyRanked {

        @DisplayName("active version이 있으면 rank 오름차순으로 반환된다.")
        @Test
        void returnsRankedList_whenActiveVersionExists() {
            // arrange
            ProductModel productA = productService.create(brand, ProductFixture.NAME + "A", ProductFixture.DESCRIPTION, ProductFixture.PRICE);
            ProductModel productB = productService.create(brand, ProductFixture.NAME + "B", ProductFixture.DESCRIPTION, ProductFixture.PRICE);

            weeklyRepository.save(new MvProductRankWeeklyEntity(productA.getId(), 2, 30.0, YEAR_WEEK, BATCH_ID));
            weeklyRepository.save(new MvProductRankWeeklyEntity(productB.getId(), 1, 50.0, YEAR_WEEK, BATCH_ID));
            activeVersionRepository.save(new MvActiveVersionEntity("WEEKLY:" + YEAR_WEEK, BATCH_ID));

            // act
            List<RankingInfo> result = mvRankingService.getWeeklyRankedAll(YEAR_WEEK);

            // assert
            assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result.get(0).rank()).isEqualTo(1L),
                () -> assertThat(result.get(0).productId()).isEqualTo(productB.getId()),
                () -> assertThat(result.get(1).rank()).isEqualTo(2L),
                () -> assertThat(result.get(1).productId()).isEqualTo(productA.getId())
            );
        }

        @DisplayName("active version이 없으면 빈 리스트를 반환한다.")
        @Test
        void returnsEmpty_whenNoActiveVersion() {
            // act
            List<RankingInfo> result = mvRankingService.getWeeklyRankedAll(YEAR_WEEK);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("MV에 있지만 삭제된 상품은 결과에서 제외된다.")
        @Test
        void skipsDeletedProduct_whenProductNotFound() {
            // arrange
            ProductModel product = productService.create(brand, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE);
            java.util.UUID ghostId = java.util.UUID.randomUUID();

            weeklyRepository.save(new MvProductRankWeeklyEntity(product.getId(), 1, 50.0, YEAR_WEEK, BATCH_ID));
            weeklyRepository.save(new MvProductRankWeeklyEntity(ghostId, 2, 30.0, YEAR_WEEK, BATCH_ID));
            activeVersionRepository.save(new MvActiveVersionEntity("WEEKLY:" + YEAR_WEEK, BATCH_ID));

            // act
            List<RankingInfo> result = mvRankingService.getWeeklyRankedAll(YEAR_WEEK);

            // assert
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).productId()).isEqualTo(product.getId())
            );
        }
    }

    @DisplayName("월간 랭킹 조회 시,")
    @Nested
    class GetMonthlyRanked {

        @DisplayName("active version이 있으면 rank 오름차순으로 반환된다.")
        @Test
        void returnsRankedList_whenActiveVersionExists() {
            // arrange
            ProductModel productA = productService.create(brand, ProductFixture.NAME + "A", ProductFixture.DESCRIPTION, ProductFixture.PRICE);
            ProductModel productB = productService.create(brand, ProductFixture.NAME + "B", ProductFixture.DESCRIPTION, ProductFixture.PRICE);

            monthlyRepository.save(new MvProductRankMonthlyEntity(productA.getId(), 2, 30.0, YEAR_MONTH, BATCH_ID));
            monthlyRepository.save(new MvProductRankMonthlyEntity(productB.getId(), 1, 50.0, YEAR_MONTH, BATCH_ID));
            activeVersionRepository.save(new MvActiveVersionEntity("MONTHLY:" + YEAR_MONTH, BATCH_ID));

            // act
            List<RankingInfo> result = mvRankingService.getMonthlyRankedAll(YEAR_MONTH);

            // assert
            assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result.get(0).rank()).isEqualTo(1L),
                () -> assertThat(result.get(0).productId()).isEqualTo(productB.getId()),
                () -> assertThat(result.get(1).rank()).isEqualTo(2L),
                () -> assertThat(result.get(1).productId()).isEqualTo(productA.getId())
            );
        }

        @DisplayName("active version이 없으면 빈 리스트를 반환한다.")
        @Test
        void returnsEmpty_whenNoActiveVersion() {
            // act
            List<RankingInfo> result = mvRankingService.getMonthlyRankedAll(YEAR_MONTH);

            // assert
            assertThat(result).isEmpty();
        }
    }
}
