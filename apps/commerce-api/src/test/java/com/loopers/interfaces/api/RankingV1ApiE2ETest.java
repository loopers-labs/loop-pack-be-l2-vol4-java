package com.loopers.interfaces.api;

import com.loopers.infrastructure.ranking.RankingKey;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 랭킹 API E2E — ZSET을 직접 시드해 조회 경로(일자 파라미터 · 상품정보 조합 · 상품 상세 rank)를 검증한다.
 *
 * <p>적재(Kafka 컨슘 → ZINCRBY)는 commerce-streamer 소관이라 여기선 ZSET을 직접 심는다. 이 테스트의 관심사는
 * <b>"ZSET에 점수가 있을 때 API가 무엇을 돌려주는가"</b>다. 적재까지 포함한 전 구간은 수동 E2E로 검증했다
 * (docs/week9/05-implementation-notes.md §4).
 *
 * <p>⚠️ Testcontainers(Redis + MySQL)가 필요하므로 Docker가 떠 있어야 실행된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final String RANKINGS_PATH = "/api/v1/rankings";
    private static final String PRODUCTS_PATH = "/api/v1/products";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired TestRestTemplate testRestTemplate;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @Autowired RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Long createBrand(String name) {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                "/api/v1/brands", HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateBrandRequest(name, "설명")),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private Long createProduct(Long brandId, String name, Long price) {
        ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCTS_PATH, HttpMethod.POST,
                new HttpEntity<>(new ProductV1Dto.CreateProductRequest(brandId, name, "설명", null, price, 10)),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    /** 해당 일자 ZSET에 (상품, 점수)를 직접 심는다 — streamer가 ZINCRBY 한 뒤와 같은 상태. */
    private void seedScore(LocalDate date, Long productId, double score) {
        redisTemplate.opsForZSet().add(RankingKey.daily(date), String.valueOf(productId), score);
    }

    private static final ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> RANKING_TYPE =
            new ParameterizedTypeReference<>() {};

    private RankingV1Dto.RankingPageResponse getRankings(String query) {
        return testRestTemplate.exchange(RANKINGS_PATH + query, HttpMethod.GET, HttpEntity.EMPTY, RANKING_TYPE)
                .getBody().data();
    }

    private static final ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>> DETAIL_TYPE =
            new ParameterizedTypeReference<>() {};

    private ProductV1Dto.ProductDetailResponse getDetail(Long productId) {
        return testRestTemplate.exchange(PRODUCTS_PATH + "/" + productId + "/detail", HttpMethod.GET,
                HttpEntity.EMPTY, DETAIL_TYPE).getBody().data();
    }

    @DisplayName("GET /api/v1/rankings — 랭킹 페이지 조회")
    @Nested
    class GetRankings {

        @DisplayName("점수 내림차순으로 순위가 매겨지고, 상품 ID가 아니라 상품 정보가 조합되어 반환된다.")
        @Test
        void returnsAggregatedProductInfo() {
            Long brandId = createBrand("나이키");
            Long low = createProduct(brandId, "조던", 199000L);
            Long high = createProduct(brandId, "에어맥스", 139000L);
            LocalDate today = LocalDate.now(RankingKey.ZONE);
            seedScore(today, high, 9.0);
            seedScore(today, low, 3.0);

            RankingV1Dto.RankingPageResponse page = getRankings("");

            assertAll(
                    () -> assertThat(page.date()).isEqualTo(today.format(YYYYMMDD)),
                    () -> assertThat(page.totalCount()).isEqualTo(2L),
                    () -> assertThat(page.items()).hasSize(2),
                    // 순위: 점수 높은 쪽이 1위
                    () -> assertThat(page.items().get(0).rank()).isEqualTo(1L),
                    () -> assertThat(page.items().get(0).productId()).isEqualTo(high),
                    () -> assertThat(page.items().get(1).rank()).isEqualTo(2L),
                    () -> assertThat(page.items().get(1).productId()).isEqualTo(low),
                    // 상품 정보 조합(ID만 주는 게 아님)
                    () -> assertThat(page.items().get(0).name()).isEqualTo("에어맥스"),
                    () -> assertThat(page.items().get(0).price()).isEqualTo(139000L),
                    () -> assertThat(page.items().get(0).brandId()).isEqualTo(brandId),
                    () -> assertThat(page.items().get(0).brandName()).isEqualTo("나이키"),
                    () -> assertThat(page.items().get(0).likesCount()).isNotNull(),
                    () -> assertThat(page.items().get(0).score()).isCloseTo(9.0, within(1e-9))
            );
        }

        @DisplayName("date를 생략하면 오늘(KST) 랭킹을 반환한다.")
        @Test
        void defaultsToToday() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct(brandId, "에어맥스", 139000L);
            LocalDate today = LocalDate.now(RankingKey.ZONE);
            seedScore(today, productId, 5.0);

            RankingV1Dto.RankingPageResponse page = getRankings("");

            assertThat(page.date()).isEqualTo(today.format(YYYYMMDD));
            assertThat(page.items()).hasSize(1);
        }

        @DisplayName("일자가 바뀌어도 date로 이전 날짜 랭킹을 조회할 수 있다(TTL 2일 내).")
        @Test
        void returnsPastDateRanking() {
            Long brandId = createBrand("나이키");
            Long yesterdayTop = createProduct(brandId, "어제1위", 10000L);
            Long todayTop = createProduct(brandId, "오늘1위", 20000L);
            LocalDate today = LocalDate.now(RankingKey.ZONE);
            LocalDate yesterday = today.minusDays(1);
            seedScore(yesterday, yesterdayTop, 7.0);
            seedScore(today, todayTop, 1.0);

            RankingV1Dto.RankingPageResponse pastPage = getRankings("?date=" + yesterday.format(YYYYMMDD));
            RankingV1Dto.RankingPageResponse todayPage = getRankings("");

            assertAll(
                    // 어제 랭킹은 어제 것만
                    () -> assertThat(pastPage.date()).isEqualTo(yesterday.format(YYYYMMDD)),
                    () -> assertThat(pastPage.items()).hasSize(1),
                    () -> assertThat(pastPage.items().get(0).productId()).isEqualTo(yesterdayTop),
                    () -> assertThat(pastPage.items().get(0).name()).isEqualTo("어제1위"),
                    // 오늘 랭킹에 어제 것이 섞이지 않는다(일자 버킷 분리)
                    () -> assertThat(todayPage.items()).hasSize(1),
                    () -> assertThat(todayPage.items().get(0).productId()).isEqualTo(todayTop)
            );
        }

        @DisplayName("랭킹이 없는 날짜는 빈 목록과 totalCount=0을 반환한다.")
        @Test
        void returnsEmptyForDateWithoutRanking() {
            LocalDate longAgo = LocalDate.now(RankingKey.ZONE).minusDays(30);

            RankingV1Dto.RankingPageResponse page = getRankings("?date=" + longAgo.format(YYYYMMDD));

            assertAll(
                    () -> assertThat(page.totalCount()).isZero(),
                    () -> assertThat(page.items()).isEmpty()
            );
        }

        @DisplayName("page/size로 페이지를 나눠 조회하며 순위가 이어진다.")
        @Test
        void paginates() {
            Long brandId = createBrand("나이키");
            LocalDate today = LocalDate.now(RankingKey.ZONE);
            Long first = createProduct(brandId, "1위", 1000L);
            Long second = createProduct(brandId, "2위", 2000L);
            Long third = createProduct(brandId, "3위", 3000L);
            seedScore(today, first, 9.0);
            seedScore(today, second, 5.0);
            seedScore(today, third, 1.0);

            RankingV1Dto.RankingPageResponse page1 = getRankings("?page=1&size=2");
            RankingV1Dto.RankingPageResponse page2 = getRankings("?page=2&size=2");

            assertAll(
                    () -> assertThat(page1.items()).hasSize(2),
                    () -> assertThat(page1.items().get(0).rank()).isEqualTo(1L),
                    () -> assertThat(page1.items().get(1).rank()).isEqualTo(2L),
                    () -> assertThat(page2.items()).hasSize(1),
                    () -> assertThat(page2.items().get(0).rank()).isEqualTo(3L),
                    () -> assertThat(page2.items().get(0).productId()).isEqualTo(third),
                    () -> assertThat(page2.totalCount()).isEqualTo(3L)
            );
        }
    }

    @DisplayName("GET /api/v1/products/{id}/detail — 상품 상세의 순위(오늘·어제)")
    @Nested
    class ProductDetailRank {

        @DisplayName("랭킹에 오른 상품은 상세 조회 시 오늘 순위가 함께 반환된다.")
        @Test
        void includesRank() {
            Long brandId = createBrand("나이키");
            Long top = createProduct(brandId, "1위", 1000L);
            Long runnerUp = createProduct(brandId, "2위", 2000L);
            LocalDate today = LocalDate.now(RankingKey.ZONE);
            seedScore(today, top, 9.0);
            seedScore(today, runnerUp, 5.0);

            assertAll(
                    () -> assertThat(getDetail(top).rank()).isEqualTo(1L),
                    () -> assertThat(getDetail(runnerUp).rank()).isEqualTo(2L)
            );
        }

        @DisplayName("랭킹에 없는 상품은 rank가 null로 반환된다.")
        @Test
        void rankIsNullWhenNotRanked() {
            Long brandId = createBrand("나이키");
            Long ranked = createProduct(brandId, "랭킹있음", 1000L);
            Long notRanked = createProduct(brandId, "랭킹없음", 2000L);
            seedScore(LocalDate.now(RankingKey.ZONE), ranked, 9.0);

            ProductV1Dto.ProductDetailResponse detail = getDetail(notRanked);

            assertAll(
                    () -> assertThat(detail.id()).isEqualTo(notRanked),
                    () -> assertThat(detail.rank()).isNull()
            );
        }

        @DisplayName("랭킹 자체가 비어 있으면 rank·rankYesterday 둘 다 null이다.")
        @Test
        void bothNullWhenRankingEmpty() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct(brandId, "에어맥스", 139000L);

            ProductV1Dto.ProductDetailResponse detail = getDetail(productId);

            assertAll(
                    () -> assertThat(detail.rank()).isNull(),
                    () -> assertThat(detail.rankYesterday()).isNull()
            );
        }
    }

    /**
     * 추세 판별 — rank/rankYesterday 조합이 4가지 상태를 구분하는지 본다.
     * 서버는 델타로 압축하지 않고 원본 둘을 주며, 한쪽이 null 인 경우가 의미를 갖는다.
     */
    @DisplayName("GET /api/v1/products/{id}/detail — 순위 추세(rank + rankYesterday)")
    @Nested
    class RankTrend {

        @DisplayName("어제보다 순위가 오르면 rank < rankYesterday 로 상승을 판별할 수 있다.")
        @Test
        void climbed() {
            Long brandId = createBrand("나이키");
            Long climber = createProduct(brandId, "상승", 1000L);
            Long other = createProduct(brandId, "기타", 2000L);
            LocalDate today = LocalDate.now(RankingKey.ZONE);
            LocalDate yesterday = today.minusDays(1);
            // 어제 2위 → 오늘 1위
            seedScore(yesterday, other, 9.0);
            seedScore(yesterday, climber, 5.0);
            seedScore(today, climber, 9.0);
            seedScore(today, other, 5.0);

            ProductV1Dto.ProductDetailResponse detail = getDetail(climber);

            assertAll(
                    () -> assertThat(detail.rank()).isEqualTo(1L),
                    () -> assertThat(detail.rankYesterday()).isEqualTo(2L)
            );
        }

        @DisplayName("어제보다 순위가 내리면 rank > rankYesterday 로 하락을 판별할 수 있다.")
        @Test
        void dropped() {
            Long brandId = createBrand("나이키");
            Long dropper = createProduct(brandId, "하락", 1000L);
            Long other = createProduct(brandId, "기타", 2000L);
            LocalDate today = LocalDate.now(RankingKey.ZONE);
            LocalDate yesterday = today.minusDays(1);
            // 어제 1위 → 오늘 2위
            seedScore(yesterday, dropper, 9.0);
            seedScore(yesterday, other, 5.0);
            seedScore(today, other, 9.0);
            seedScore(today, dropper, 5.0);

            ProductV1Dto.ProductDetailResponse detail = getDetail(dropper);

            assertAll(
                    () -> assertThat(detail.rank()).isEqualTo(2L),
                    () -> assertThat(detail.rankYesterday()).isEqualTo(1L)
            );
        }

        @DisplayName("오늘 처음 진입한 상품은 rank는 있고 rankYesterday는 null이다(신규 진입).")
        @Test
        void newEntry() {
            Long brandId = createBrand("나이키");
            Long newcomer = createProduct(brandId, "신규진입", 1000L);
            seedScore(LocalDate.now(RankingKey.ZONE), newcomer, 9.0);

            ProductV1Dto.ProductDetailResponse detail = getDetail(newcomer);

            assertAll(
                    () -> assertThat(detail.rank()).isEqualTo(1L),
                    () -> assertThat(detail.rankYesterday()).isNull()
            );
        }

        @DisplayName("어제만 랭킹에 올랐던 상품은 rank는 null이고 rankYesterday는 남는다(이탈).")
        @Test
        void droppedOut() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct(brandId, "어제만1위", 1000L);
            seedScore(LocalDate.now(RankingKey.ZONE).minusDays(1), productId, 9.0);

            ProductV1Dto.ProductDetailResponse detail = getDetail(productId);

            assertAll(
                    // 상세의 rank는 "오늘 뜨는가"라 어제 1위여도 오늘 활동이 없으면 null
                    () -> assertThat(detail.rank()).isNull(),
                    // 하지만 어제 순위는 남아 "이탈"임을 구분할 수 있다 (이틀간 무관과 다르다)
                    () -> assertThat(detail.rankYesterday()).isEqualTo(1L)
            );
        }
    }
}
