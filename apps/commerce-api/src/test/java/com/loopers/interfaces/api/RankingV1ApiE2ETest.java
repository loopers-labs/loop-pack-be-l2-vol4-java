package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  private final TestRestTemplate testRestTemplate;
  private final ProductJpaRepository productJpaRepository;
  private final RedisTemplate<String, String> masterRedisTemplate;
  private final DatabaseCleanUp databaseCleanUp;
  private final RedisCleanUp redisCleanUp;

  @Autowired
  RankingV1ApiE2ETest(
      TestRestTemplate testRestTemplate,
      ProductJpaRepository productJpaRepository,
      @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
          RedisTemplate<String, String> masterRedisTemplate,
      DatabaseCleanUp databaseCleanUp,
      RedisCleanUp redisCleanUp) {
    this.testRestTemplate = testRestTemplate;
    this.productJpaRepository = productJpaRepository;
    this.masterRedisTemplate = masterRedisTemplate;
    this.databaseCleanUp = databaseCleanUp;
    this.redisCleanUp = redisCleanUp;
  }

  @DisplayName("GET /api/v1/rankings/hourly")
  @Nested
  class GetHourlyRankings {

    @DisplayName("지정한 시간 ZSET의 랭킹을 상품 정보 및 페이지 메타와 함께 반환한다.")
    @Test
    void returnsHourlyRankingsWithProductsAndPageMetadata() {
      LocalDateTime dateTime = LocalDateTime.of(2026, 7, 16, 23, 0);
      ProductModel first = createProduct("시간 1위", 3000L);
      ProductModel second = createProduct("시간 2위", 2000L);
      ProductModel third = createProduct("시간 3위", 1000L);
      addHourlyScore(dateTime, first.getId(), 3.0);
      addHourlyScore(dateTime, second.getId(), 2.0);
      addHourlyScore(dateTime, third.getId(), 1.0);

      ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
          getHourlyRankings("?datetime=2026071623&page=2&size=2");

      RankingV1Dto.RankingPageResponse data = response.getBody().data();
      assertAll(
          () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
          () -> assertThat(data.items()).hasSize(1),
          () -> assertThat(data.items().getFirst().rank()).isEqualTo(3L),
          () -> assertThat(data.items().getFirst().score()).isEqualTo(1.0),
          () -> assertThat(data.items().getFirst().product().id()).isEqualTo(third.getId()),
          () -> assertThat(data.page()).isEqualTo(2),
          () -> assertThat(data.size()).isEqualTo(2),
          () -> assertThat(data.totalCount()).isEqualTo(3),
          () -> assertThat(data.totalPages()).isEqualTo(2));
    }

    @DisplayName("존재하지 않는 날짜·시간 형식과 잘못된 페이지 요청은 400을 반환한다.")
    @Test
    void returnsBadRequestForInvalidHourlyQuery() {
      ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> missingDateTime =
          getHourlyRankings("");
      ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> invalidDate =
          getHourlyRankings("?datetime=2026023001");
      ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> invalidHour =
          getHourlyRankings("?datetime=2026071624");
      ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> invalidPage =
          getHourlyRankings("?datetime=2026071623&page=0");
      ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> invalidSize =
          getHourlyRankings("?datetime=2026071623&size=101");

      assertAll(
          () -> assertThat(missingDateTime.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
          () -> assertThat(invalidDate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
          () -> assertThat(invalidHour.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
          () -> assertThat(invalidPage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
          () -> assertThat(invalidSize.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
  }

  @AfterEach
  void tearDown() {
    databaseCleanUp.truncateAllTables();
    redisCleanUp.truncateAll();
  }

  @DisplayName("GET /api/v1/rankings")
  @Nested
  class GetRankings {

    @DisplayName("점수 내림차순의 랭킹과 상품 전체 정보 및 1-base 페이지 메타를 반환한다.")
    @Test
    void returnsRankingsWithProductsAndPageMetadata() {
      LocalDate date = LocalDate.now(SEOUL_ZONE).minusDays(1);
      ProductModel first = createProduct("1위 상품", 3000L);
      ProductModel second = createProduct("2위 상품", 2000L);
      ProductModel third = createProduct("3위 상품", 1000L);
      addScore(date, first.getId(), 3.0);
      addScore(date, second.getId(), 2.0);
      addScore(date, third.getId(), 1.0);

      ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
          getRankings(
              "?date="
                  + date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                  + "&page=1&size=2");

      RankingV1Dto.RankingPageResponse data = response.getBody().data();
      assertAll(
          () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
          () ->
              assertThat(data.items())
                  .extracting(RankingV1Dto.RankingItemResponse::rank)
                  .containsExactly(1L, 2L),
          () ->
              assertThat(data.items())
                  .extracting(RankingV1Dto.RankingItemResponse::score)
                  .containsExactly(3.0, 2.0),
          () ->
              assertThat(data.items())
                  .extracting(item -> item.product().id())
                  .containsExactly(first.getId(), second.getId()),
          () -> assertThat(data.items().getFirst().product().name()).isEqualTo("1위 상품"),
          () -> assertThat(data.items().getFirst().product().price()).isEqualTo(3000L),
          () -> assertThat(data.page()).isEqualTo(1),
          () -> assertThat(data.size()).isEqualTo(2),
          () -> assertThat(data.totalCount()).isEqualTo(3),
          () -> assertThat(data.totalPages()).isEqualTo(2));
    }

    @DisplayName("ZSET에만 남은 삭제 상품은 결과에서 제외하되 원래 순위는 유지한다.")
    @Test
    void excludesDeletedProductAndPreservesZSetRank() {
      LocalDate date = LocalDate.now(SEOUL_ZONE);
      ProductModel deleted = createProduct("삭제 상품", 3000L);
      ProductModel active = createProduct("정상 상품", 2000L);
      addScore(date, deleted.getId(), 3.0);
      addScore(date, active.getId(), 2.0);
      deleted.delete();
      productJpaRepository.saveAndFlush(deleted);

      ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
          getRankings("?page=1&size=20");

      RankingV1Dto.RankingPageResponse data = response.getBody().data();
      assertAll(
          () -> assertThat(data.items()).hasSize(1),
          () -> assertThat(data.items().getFirst().product().id()).isEqualTo(active.getId()),
          () -> assertThat(data.items().getFirst().rank()).isEqualTo(2L),
          () -> assertThat(data.totalCount()).isEqualTo(2));
    }

    @DisplayName("유효하지 않은 날짜와 페이지 요청은 400을 반환한다.")
    @Test
    void returnsBadRequestForInvalidQuery() {
      ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> invalidDate =
          getRankings("?date=20260230");
      ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> invalidPage =
          getRankings("?page=0");
      ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> invalidSize =
          getRankings("?size=101");

      assertAll(
          () -> assertThat(invalidDate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
          () -> assertThat(invalidPage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
          () -> assertThat(invalidSize.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
  }

  @DisplayName("상품 상세 랭킹")
  @Nested
  class ProductDetailRanking {

    @DisplayName("오늘 랭킹의 1-base 순위를 상세 응답에 추가한다.")
    @Test
    void returnsTodayRankInProductDetail() {
      LocalDate today = LocalDate.now(SEOUL_ZONE);
      ProductModel first = createProduct("1위 상품", 3000L);
      ProductModel second = createProduct("2위 상품", 2000L);
      addScore(today, first.getId(), 3.0);
      addScore(today, second.getId(), 2.0);

      ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response =
          getProductDetail(second.getId());

      assertAll(
          () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
          () -> assertThat(response.getBody().data().id()).isEqualTo(second.getId()),
          () -> assertThat(response.getBody().data().rank()).isEqualTo(2L));
    }

    @DisplayName("오늘 랭킹에 없는 상품은 상세 응답의 rank를 null로 반환한다.")
    @Test
    void returnsNullRankWhenProductIsNotRanked() {
      ProductModel product = createProduct("미등재 상품", 1000L);

      ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response =
          getProductDetail(product.getId());
      ResponseEntity<String> rawResponse =
          testRestTemplate.getForEntity("/api/v1/products/" + product.getId(), String.class);

      assertThat(response.getBody().data().rank()).isNull();
      assertThat(rawResponse.getBody()).contains("\"rank\":null");
    }

    @DisplayName("상품 상세 캐시가 적중해도 rank는 오늘 ZSET의 최신 값을 반환한다.")
    @Test
    void refreshesRankOnEveryDetailRequestWhileProductIsCached() {
      LocalDate today = LocalDate.now(SEOUL_ZONE);
      ProductModel first = createProduct("기존 1위", 3000L);
      ProductModel target = createProduct("순위 변경 상품", 2000L);
      addScore(today, first.getId(), 2.0D);
      addScore(today, target.getId(), 1.0D);

      ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> firstResponse =
          getProductDetail(target.getId());
      addScore(today, target.getId(), 3.0D);
      ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> cachedResponse =
          getProductDetail(target.getId());

      assertAll(
          () -> assertThat(firstResponse.getBody().data().rank()).isEqualTo(2L),
          () -> assertThat(cachedResponse.getBody().data().rank()).isEqualTo(1L));
    }
  }

  private ProductModel createProduct(String name, long price) {
    return productJpaRepository.saveAndFlush(new ProductModel(name, "상품 설명", price, 10, 1L));
  }

  private void addScore(LocalDate date, Long productId, double score) {
    masterRedisTemplate
        .opsForZSet()
        .add(RankingRedisRepository.dailyKey(date), productId.toString(), score);
  }

  private void addHourlyScore(LocalDateTime dateTime, Long productId, double score) {
    masterRedisTemplate
        .opsForZSet()
        .add(RankingRedisRepository.hourlyKey(dateTime), productId.toString(), score);
  }

  private ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> getRankings(String query) {
    return testRestTemplate.exchange(
        "/api/v1/rankings" + query,
        HttpMethod.GET,
        new HttpEntity<>(null),
        new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> getHourlyRankings(
      String query) {
    return testRestTemplate.exchange(
        "/api/v1/rankings/hourly" + query,
        HttpMethod.GET,
        new HttpEntity<>(null),
        new ParameterizedTypeReference<>() {});
  }

  private ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> getProductDetail(
      Long productId) {
    return testRestTemplate.exchange(
        "/api/v1/products/" + productId,
        HttpMethod.GET,
        new HttpEntity<>(null),
        new ParameterizedTypeReference<>() {});
  }
}
