package com.loopers.interfaces.api;

import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockEntity;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.infrastructure.user.UserEntity;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String PRODUCTS_URL = "/api/v1/products";
    private static final String USERS_URL = "/api/v1/users";

    private static final String LOGIN_ID = "likeuser";
    private static final String LOGIN_PW = "pAssWord1!";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private ProductStockJpaRepository productStockJpaRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisTemplate<String, String> redisTemplate;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(new UserV1Dto.UserJoinRequest(LOGIN_ID, LOGIN_PW, "루퍼스", LocalDate.of(2000, 1, 1), "like@test.com")),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        );
        userId = userJpaRepository.findByLoginId(LOGIN_ID).orElseThrow().getId();

        BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
        ProductEntity product = productJpaRepository.save(new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
        productStockJpaRepository.save(new ProductStockEntity(product.getId(), 10L));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisTemplate.delete(redisTemplate.keys("product:like:pending:*"));
    }

    private String likePendingKey(Long pid) {
        return "product:like:pending:" + pid;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", LOGIN_PW);
        return headers;
    }

    private String likesUrl(Long pid) {
        return PRODUCTS_URL + "/" + pid + "/likes";
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class LikeProduct {

        @DisplayName("좋아요하면 200을 반환하고 Redis delta가 1 증가한다.")
        @Test
        void returnsOk_andIncrementsLikeDeltaInRedis() {
            ResponseEntity<ApiResponse<Void>> likeResponse = testRestTemplate.exchange(
                likesUrl(productId), HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(likeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(redisTemplate.opsForValue().get(likePendingKey(productId))).isEqualTo("1")
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요해도 200을 반환하고 Redis delta는 1을 유지한다.")
        @Test
        void returnsOk_andLikeDeltaStaysAt1_whenAlreadyLiked() {
            testRestTemplate.exchange(likesUrl(productId), HttpMethod.POST,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<ApiResponse<Void>>() {});

            testRestTemplate.exchange(likesUrl(productId), HttpMethod.POST,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<ApiResponse<Void>>() {});

            assertThat(redisTemplate.opsForValue().get(likePendingKey(productId))).isEqualTo("1");
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                likesUrl(9999L), HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenHeaderIsMissing() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                likesUrl(productId), HttpMethod.POST,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class UnlikeProduct {

        @DisplayName("좋아요를 취소하면 200을 반환하고 Redis delta가 0이 된다.")
        @Test
        void returnsOk_andLikeDeltaIsZero_whenUnliked() {
            testRestTemplate.exchange(likesUrl(productId), HttpMethod.POST,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<ApiResponse<Void>>() {});

            ResponseEntity<ApiResponse<Void>> unlikeResponse = testRestTemplate.exchange(
                likesUrl(productId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(unlikeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(redisTemplate.opsForValue().get(likePendingKey(productId))).isEqualTo("0")
            );
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도 200을 반환한다.")
        @Test
        void returnsOk_whenUnlikeWithoutPriorLike() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                likesUrl(productId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    class GetLikedProducts {

        @DisplayName("본인의 좋아요 목록을 조회하면, 200과 좋아요한 상품 목록을 반환한다.")
        @Test
        void returnsLikedProducts_whenUserIdMatchesAuthenticatedUser() {
            testRestTemplate.exchange(likesUrl(productId), HttpMethod.POST,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<ApiResponse<Void>>() {});

            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                USERS_URL + "/" + userId + "/likes", HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).id()).isEqualTo(productId)
            );
        }

        @DisplayName("타 유저의 좋아요 목록을 조회하면, 403을 반환한다.")
        @Test
        void returnsForbidden_whenUserIdDoesNotMatchAuthenticatedUser() {
            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                USERS_URL + "/9999/likes", HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("인증 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenHeaderIsMissing() {
            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                USERS_URL + "/" + userId + "/likes", HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
