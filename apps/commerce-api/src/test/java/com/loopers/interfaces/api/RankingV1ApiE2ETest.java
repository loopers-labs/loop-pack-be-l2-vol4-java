package com.loopers.interfaces.api;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.money.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.ranking.RankingKeys;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public RankingV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.redisTemplate = redisTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

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

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class Get {

        @DisplayName("date/page/size로 조회하면, 점수 순으로 정렬된 랭킹 페이지를 반환한다.")
        @Test
        void returnsRankingPage() {
            // arrange
            LocalDate date = LocalDate.of(2025, 9, 6);
            Product first = saveProduct("1등 상품");
            Product second = saveProduct("2등 상품");
            seedScore(date, first.getId(), 90.0);
            seedScore(date, second.getId(), 50.0);
            String dateParam = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String requestUrl = "/api/v1/rankings?date=" + dateParam + "&page=1&size=20";

            // act
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().items()).hasSize(2),
                () -> assertThat(response.getBody().data().items().get(0).rank()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().items().get(0).product().id()).isEqualTo(first.getId()),
                () -> assertThat(response.getBody().data().items().get(1).rank()).isEqualTo(2L),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2L)
            );
        }

        @DisplayName("date를 생략하면, 오늘 날짜 기준으로 조회한다.")
        @Test
        void usesTodayDate_whenDateOmitted() {
            // arrange
            Product product = saveProduct("오늘의 상품");
            seedScore(LocalDate.now(), product.getId(), 42.0);

            // act
            ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
                testRestTemplate.exchange("/api/v1/rankings", HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).product().id()).isEqualTo(product.getId())
            );
        }
    }
}
