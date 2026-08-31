package com.loopers.interfaces.api;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.MvActiveVersionEntity;
import com.loopers.domain.ranking.MvProductRankMonthlyEntity;
import com.loopers.domain.ranking.MvProductRankWeeklyEntity;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.infrastructure.ranking.MvActiveVersionJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final String RANKING_URL       = "/api/v1/rankings";
    private static final String PRODUCT_URL       = "/api/v1/products";
    private static final String ADMIN_BRAND_URL   = "/api-admin/v1/brands";
    private static final String ADMIN_PRODUCT_URL = "/api-admin/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private MvProductRankWeeklyJpaRepository weeklyRepository;

    @Autowired
    private MvProductRankMonthlyJpaRepository monthlyRepository;

    @Autowired
    private MvActiveVersionJpaRepository activeVersionRepository;

    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void tearDown() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        databaseCleanUp.truncateAllTables();
        Set<String> keys = redisTemplate.keys("ranking:all:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private int currentYearWeek() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        int year = today.get(WeekFields.ISO.weekBasedYear());
        int week = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        return year * 100 + week;
    }

    private int currentYearMonth() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return today.getYear() * 100 + today.getMonthValue();
    }

    private String today() {
        return LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private UUID createBrand() {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            ADMIN_BRAND_URL, HttpMethod.POST,
            new HttpEntity<>(new BrandV1Dto.CreateRequest(BrandFixture.NAME, BrandFixture.DESCRIPTION), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private UUID createProduct(UUID brandId) {
        ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> response = testRestTemplate.exchange(
            ADMIN_PRODUCT_URL, HttpMethod.POST,
            new HttpEntity<>(new ProductV1Dto.CreateRequest(
                brandId, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE, ProductFixture.INITIAL_QUANTITY
            ), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    @Nested
    class 랭킹_목록_조회 {

        @Test
        void 점수_높은_순으로_상품정보와_함께_반환된다() {
            // arrange
            UUID brandId = createBrand();
            UUID productA = createProduct(brandId);
            UUID productB = createProduct(brandId);
            String key = "ranking:all:" + today();
            redisTemplate.opsForZSet().add(key, productA.toString(), 3.0);
            redisTemplate.opsForZSet().add(key, productB.toString(), 5.0);

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingResponse>>> response =
                testRestTemplate.exchange(
                    RANKING_URL + "?date=" + today() + "&size=20&page=1",
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getContent()).hasSize(2),
                () -> assertThat(response.getBody().data().getContent().get(0).rank()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().getContent().get(0).productId()).isEqualTo(productB),
                () -> assertThat(response.getBody().data().getContent().get(1).rank()).isEqualTo(2L),
                () -> assertThat(response.getBody().data().getContent().get(1).productId()).isEqualTo(productA)
            );
        }

        @Test
        void 데이터_없으면_빈_목록을_반환한다() {
            // act
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingResponse>>> response =
                testRestTemplate.exchange(
                    RANKING_URL + "?date=" + today() + "&size=20&page=1",
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getContent()).isEmpty(),
                () -> assertThat(response.getBody().data().getTotalElements()).isZero()
            );
        }
    }

    @DisplayName("주간 랭킹 조회 시,")
    @Nested
    class 주간_랭킹_목록_조회 {

        @DisplayName("MV 데이터가 있으면 rank 순으로 상품 정보와 함께 반환된다.")
        @Test
        void returnsWeeklyRanking_whenMvDataExists() {
            // arrange
            UUID brandId = createBrand();
            UUID productA = createProduct(brandId);
            UUID productB = createProduct(brandId);

            int yearWeek = currentYearWeek();
            long batchId = 9000L;
            weeklyRepository.saveAll(List.of(
                new MvProductRankWeeklyEntity(productA, 2, 30.0, yearWeek, batchId),
                new MvProductRankWeeklyEntity(productB, 1, 50.0, yearWeek, batchId)
            ));
            activeVersionRepository.save(new MvActiveVersionEntity("WEEKLY:" + yearWeek, batchId));

            String date = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingResponse>>> response =
                testRestTemplate.exchange(
                    RANKING_URL + "?type=WEEKLY&date=" + date + "&size=20&page=1",
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getContent()).hasSize(2),
                () -> assertThat(response.getBody().data().getContent().get(0).rank()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().getContent().get(0).productId()).isEqualTo(productB),
                () -> assertThat(response.getBody().data().getContent().get(1).rank()).isEqualTo(2L),
                () -> assertThat(response.getBody().data().getContent().get(1).productId()).isEqualTo(productA)
            );
        }

        @DisplayName("배치가 아직 실행되지 않았으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoMvData() {
            // arrange
            String date = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingResponse>>> response =
                testRestTemplate.exchange(
                    RANKING_URL + "?type=WEEKLY&date=" + date + "&size=20&page=1",
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getContent()).isEmpty()
            );
        }
    }

    @DisplayName("월간 랭킹 조회 시,")
    @Nested
    class 월간_랭킹_목록_조회 {

        @DisplayName("MV 데이터가 있으면 rank 순으로 상품 정보와 함께 반환된다.")
        @Test
        void returnsMonthlyRanking_whenMvDataExists() {
            // arrange
            UUID brandId = createBrand();
            UUID productA = createProduct(brandId);
            UUID productB = createProduct(brandId);

            int yearMonth = currentYearMonth();
            long batchId = 9001L;
            monthlyRepository.saveAll(List.of(
                new MvProductRankMonthlyEntity(productA, 2, 30.0, yearMonth, batchId),
                new MvProductRankMonthlyEntity(productB, 1, 50.0, yearMonth, batchId)
            ));
            activeVersionRepository.save(new MvActiveVersionEntity("MONTHLY:" + yearMonth, batchId));

            String date = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMM"));

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingResponse>>> response =
                testRestTemplate.exchange(
                    RANKING_URL + "?type=MONTHLY&date=" + date + "&size=20&page=1",
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getContent()).hasSize(2),
                () -> assertThat(response.getBody().data().getContent().get(0).rank()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().getContent().get(0).productId()).isEqualTo(productB),
                () -> assertThat(response.getBody().data().getContent().get(1).rank()).isEqualTo(2L),
                () -> assertThat(response.getBody().data().getContent().get(1).productId()).isEqualTo(productA)
            );
        }

        @DisplayName("배치가 아직 실행되지 않았으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoMvData() {
            // arrange
            String date = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMM"));

            // act
            ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingResponse>>> response =
                testRestTemplate.exchange(
                    RANKING_URL + "?type=MONTHLY&date=" + date + "&size=20&page=1",
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getContent()).isEmpty()
            );
        }
    }

    @Nested
    class 상품_상세_조회_랭킹 {

        @Test
        void 랭킹에_있는_상품_조회_시_rank가_반환된다() {
            // arrange
            UUID brandId = createBrand();
            UUID productId = createProduct(brandId);
            String key = "ranking:all:" + today();
            redisTemplate.opsForZSet().add(key, productId.toString(), 5.0);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(
                    PRODUCT_URL + "/" + productId,
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().rank()).isEqualTo(1L)
            );
        }

        @Test
        void 랭킹에_없는_상품_조회_시_rank가_null이다() {
            // arrange
            UUID brandId = createBrand();
            UUID productId = createProduct(brandId);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(
                    PRODUCT_URL + "/" + productId,
                    HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().rank()).isNull()
            );
        }
    }
}
