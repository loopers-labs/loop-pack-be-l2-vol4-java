package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.ranking.RankingDto;
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
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingApiE2ETest {

    private static final String RANKINGS_URL = "/api/v1/rankings?date=20260717&size=20&page=1";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    class GetRankings {

        @DisplayName("ZSET 점수 내림차순으로 상품정보가 Aggregation되어 반환된다.")
        @Test
        void returnsRankedProducts_withAggregatedInfo() {
            BrandModel brand = brandRepository.save(new BrandModel("브랜드A"));
            ProductModel productA = productRepository.save(new ProductModel("상품A", 10000L, brand.getId()));
            ProductModel productB = productRepository.save(new ProductModel("상품B", 20000L, brand.getId()));
            redisTemplate.opsForZSet().add("ranking:all:20260717", String.valueOf(productB.getId()), 9.0);
            redisTemplate.opsForZSet().add("ranking:all:20260717", String.valueOf(productA.getId()), 5.0);

            ResponseEntity<ApiResponse<RankingDto.RankingPageResponse>> response = testRestTemplate.exchange(
                RANKINGS_URL, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var body = response.getBody().data();
            assertThat(body.rankings()).hasSize(2);
            assertThat(body.rankings().get(0).productId()).isEqualTo(productB.getId());
            assertThat(body.rankings().get(0).rank()).isEqualTo(1);
            assertThat(body.rankings().get(0).productName()).isEqualTo("상품B");
            assertThat(body.rankings().get(1).productId()).isEqualTo(productA.getId());
            assertThat(body.rankings().get(1).rank()).isEqualTo(2);
            assertThat(body.totalElements()).isEqualTo(2L);
        }

        @DisplayName("해당 날짜에 랭킹 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoRankingData() {
            ResponseEntity<ApiResponse<RankingDto.RankingPageResponse>> response = testRestTemplate.exchange(
                RANKINGS_URL, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().rankings()).isEmpty();
            assertThat(response.getBody().data().totalElements()).isEqualTo(0L);
        }
    }
}
