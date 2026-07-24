package com.loopers.application.product;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductApplicationServiceRankingTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private void seedRanking(LocalDate date, String productId, double score) {
        redisTemplate.opsForZSet().add("ranking:all:" + date.format(DATE_FORMAT), productId, score);
    }

    private void seedWeeklyMv(LocalDate asOfDate, String productId, double score) {
        jdbcTemplate.update("""
                INSERT INTO mv_product_rank_weekly (as_of_date, product_id, score, view_sum, like_delta_sum, purchase_quantity_sum, created_at)
                VALUES (?, ?, ?, 0, 0, 0, ?)
                """, asOfDate, productId, score, ZonedDateTime.now());
    }

    @DisplayName("getRankedProducts")
    @Nested
    class GetRankedProducts {

        @DisplayName("[ECP] 랭킹 데이터가 있으면 score 내림차순으로 정렬된 상품 정보를 rank와 함께 반환한다.")
        @Test
        void returnsRankedProductInfo_whenRankingDataExists() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 16);
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo first = productApplicationService.createProduct(brand.id(), "에어맥스", "설명", 100_000L, 10);
            ProductInfo second = productApplicationService.createProduct(brand.id(), "에어포스", "설명", 120_000L, 5);
            seedRanking(date, first.id(), 300.0);
            seedRanking(date, second.id(), 100.0);

            // act
            Page<RankingInfo> result = productApplicationService.getRankedProducts(date, RankingPeriod.DAILY, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).rank()).isEqualTo(1);
            assertThat(result.getContent().get(0).product().id()).isEqualTo(first.id());
            assertThat(result.getContent().get(1).rank()).isEqualTo(2);
            assertThat(result.getContent().get(1).product().id()).isEqualTo(second.id());
        }

        @DisplayName("[ECP] 해당 일자의 랭킹 데이터가 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenRankingDataDoesNotExist() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productApplicationService.getRankedProducts(LocalDate.of(2099, 1, 1), RankingPeriod.DAILY, PageRequest.of(0, 20)));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[Error Guessing] ZSET에는 있으나 DB에서 결손된 상품은 결과에서 제외된다.")
        @Test
        void skipsMissingProduct_whenProductDoesNotExistInDb() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 16);
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo existing = productApplicationService.createProduct(brand.id(), "에어맥스", "설명", 100_000L, 10);
            seedRanking(date, existing.id(), 300.0);
            seedRanking(date, "PRD_DOES_NOT_EXIST", 200.0);

            // act
            Page<RankingInfo> result = productApplicationService.getRankedProducts(date, RankingPeriod.DAILY, PageRequest.of(0, 20));

            // assert
            // countByDate(ZCARD)는 2이지만, PageImpl(content, pageable, total) 생성자는
            // pageable.getOffset()+getPageSize() > total 이고 content가 비어있지 않을 때
            // total을 offset+content.size()로 자동 보정한다(Spring Data 기본 동작).
            // 상품 삭제 시 Redis 랭킹 데이터도 함께 정리되는 것으로 간주하므로 이 skip은
            // 예외적 상황이며, totalElements가 실제 반환된 content 크기로 줄어드는 것을 허용한다.
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).product().id()).isEqualTo(existing.id());
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @DisplayName("[Boundary] page/size에 따라 offset 기준으로 페이징된다.")
        @Test
        void paginatesByOffset() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 16);
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo first = productApplicationService.createProduct(brand.id(), "1위", "설명", 100_000L, 10);
            ProductInfo second = productApplicationService.createProduct(brand.id(), "2위", "설명", 100_000L, 10);
            ProductInfo third = productApplicationService.createProduct(brand.id(), "3위", "설명", 100_000L, 10);
            seedRanking(date, first.id(), 300.0);
            seedRanking(date, second.id(), 200.0);
            seedRanking(date, third.id(), 100.0);

            // act
            Page<RankingInfo> result = productApplicationService.getRankedProducts(date, RankingPeriod.DAILY, PageRequest.of(1, 2));

            // assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).product().id()).isEqualTo(third.id());
            assertThat(result.getContent().get(0).rank()).isEqualTo(3);
        }

        @DisplayName("[ECP] period가 WEEKLY이면 RDB MV(mv_product_rank_weekly)를 조회한다.")
        @Test
        void routesToRdbMv_whenPeriodIsWeekly() {
            // arrange
            LocalDate asOfDate = LocalDate.of(2026, 7, 23);
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo first = productApplicationService.createProduct(brand.id(), "에어맥스", "설명", 100_000L, 10);
            ProductInfo second = productApplicationService.createProduct(brand.id(), "에어포스", "설명", 120_000L, 5);
            seedWeeklyMv(asOfDate, first.id(), 30.0);
            seedWeeklyMv(asOfDate, second.id(), 10.0);
            // DAILY(Redis)에는 동일 날짜에 다른 순서로 시딩해, 실제로 WEEKLY 경로(RDB MV)를 탔는지 구분한다.
            seedRanking(asOfDate, second.id(), 999.0);
            seedRanking(asOfDate, first.id(), 1.0);

            // act
            Page<RankingInfo> result = productApplicationService.getRankedProducts(asOfDate, RankingPeriod.WEEKLY, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).product().id()).isEqualTo(first.id());
            assertThat(result.getContent().get(1).product().id()).isEqualTo(second.id());
        }

        @DisplayName("[ECP] 해당 as_of_date의 WEEKLY MV 스냅샷이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenWeeklyMvSnapshotDoesNotExist() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> productApplicationService.getRankedProducts(LocalDate.of(2099, 1, 1), RankingPeriod.WEEKLY, PageRequest.of(0, 20)));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[Boundary] WEEKLY는 TOP 100까지만 노출되며, 100위 경계를 걸치는 페이지는 100위까지만 반환한다.")
        @Test
        void capsAtTop100_whenPageSpansThe100thRank() {
            // arrange
            LocalDate asOfDate = LocalDate.of(2026, 7, 23);
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            int totalProducts = 105;
            for (int i = 1; i <= totalProducts; i++) {
                ProductInfo product = productApplicationService.createProduct(brand.id(), "상품" + i, "설명", 10_000L, 10);
                seedWeeklyMv(asOfDate, product.id(), totalProducts - i); // i가 작을수록 score가 높다(=순위가 높다)
            }

            // act — offset=90, size=30 → 91~120위를 요청하지만 100위까지만 존재
            Page<RankingInfo> result = productApplicationService.getRankedProducts(asOfDate, RankingPeriod.WEEKLY, PageRequest.of(3, 30));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(100L);
            assertThat(result.getContent()).hasSize(10);
            assertThat(result.getContent().get(0).rank()).isEqualTo(91);
            assertThat(result.getContent().get(9).rank()).isEqualTo(100);
        }

        @DisplayName("[Boundary] WEEKLY에서 100위를 완전히 넘는 페이지를 요청하면 빈 목록을 반환한다(404 아님).")
        @Test
        void returnsEmptyContent_whenPageIsEntirelyBeyondTop100() {
            // arrange
            LocalDate asOfDate = LocalDate.of(2026, 7, 23);
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            int totalProducts = 105;
            for (int i = 1; i <= totalProducts; i++) {
                ProductInfo product = productApplicationService.createProduct(brand.id(), "상품" + i, "설명", 10_000L, 10);
                seedWeeklyMv(asOfDate, product.id(), totalProducts - i);
            }

            // act — offset=100 → 101위부터 요청, 100위까지만 존재하므로 완전히 범위 밖
            Page<RankingInfo> result = productApplicationService.getRankedProducts(asOfDate, RankingPeriod.WEEKLY, PageRequest.of(5, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(100L);
            assertThat(result.getContent()).isEmpty();
        }
    }
}
