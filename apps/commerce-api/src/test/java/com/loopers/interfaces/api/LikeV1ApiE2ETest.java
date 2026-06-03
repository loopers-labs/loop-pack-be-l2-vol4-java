package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.like.LikeV1Dto;
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
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @Autowired
    public LikeV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        signUp("testid", "testPw1234", "테스터");
        Long brandId = createBrand("나이키", "스포츠");
        productId = createProduct(brandId, "에어맥스", 139000L, 10);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders(String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", loginPw);
        return headers;
    }

    private void signUp(String loginId, String password, String name) {
        testRestTemplate.postForEntity("/api/v1/users",
                new UserV1Dto.SignUpRequest(loginId, password, name, LocalDate.of(1992, 6, 24), "test@example.com"),
                Object.class);
    }

    private Long createBrand(String name, String description) {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                "/api/v1/brands", HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateBrandRequest(name, description)),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private Long createProduct(Long brandId, String name, Long price, Integer stock) {
        ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products", HttpMethod.POST,
                new HttpEntity<>(new ProductV1Dto.CreateProductRequest(brandId, name, "설명", null, price, stock)),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private String likePath() {
        return "/api/v1/products/" + productId + "/like";
    }

    private static final ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikedResponse>> LIKE_TYPE =
            new ParameterizedTypeReference<>() {};

    @DisplayName("POST /api/v1/products/{productId}/like")
    @Nested
    class Like {

        @DisplayName("인증된 사용자가 좋아요하면, 200과 liked=true를 반환한다.")
        @Test
        void returnsLikedTrue_whenAuthenticated() {
            ResponseEntity<ApiResponse<LikeV1Dto.LikedResponse>> response = testRestTemplate.exchange(
                    likePath(), HttpMethod.POST,
                    new HttpEntity<>(authHeaders("testid", "testPw1234")), LIKE_TYPE);

            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(response.getBody().data().liked()).isTrue()
            );
        }

        @DisplayName("잘못된 비밀번호로 좋아요하면, 401을 반환한다.")
        @Test
        void returns401_whenWrongPassword() {
            ResponseEntity<Object> response = testRestTemplate.exchange(
                    likePath(), HttpMethod.POST,
                    new HttpEntity<>(authHeaders("testid", "wrongPw9999")), Object.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("좋아요 → 조회 → 취소 흐름")
    @Nested
    class LikeFlow {

        @DisplayName("좋아요 후 조회하면 liked=true, 취소 후 조회하면 liked=false다. (멱등)")
        @Test
        void reflectsLikeState() {
            HttpEntity<Void> auth = new HttpEntity<>(authHeaders("testid", "testPw1234"));

            testRestTemplate.exchange(likePath(), HttpMethod.POST, auth, LIKE_TYPE);
            ResponseEntity<ApiResponse<LikeV1Dto.LikedResponse>> afterLike =
                    testRestTemplate.exchange(likePath(), HttpMethod.GET, auth, LIKE_TYPE);

            testRestTemplate.exchange(likePath(), HttpMethod.DELETE, auth, LIKE_TYPE);
            ResponseEntity<ApiResponse<LikeV1Dto.LikedResponse>> afterUnlike =
                    testRestTemplate.exchange(likePath(), HttpMethod.GET, auth, LIKE_TYPE);

            assertAll(
                    () -> assertThat(afterLike.getBody().data().liked()).isTrue(),
                    () -> assertThat(afterUnlike.getBody().data().liked()).isFalse()
            );
        }
    }

    @DisplayName("GET /api/v1/users/me/likes")
    @Nested
    class GetMyLikedProducts {

        private static final ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductResponse>>> LIST_TYPE =
                new ParameterizedTypeReference<>() {};

        @DisplayName("좋아요한 상품 목록을 조회하면, 좋아요한 활성 상품이 반환된다.")
        @Test
        void returnsLikedProducts() {
            HttpEntity<Void> auth = new HttpEntity<>(authHeaders("testid", "testPw1234"));
            testRestTemplate.exchange(likePath(), HttpMethod.POST, auth, LIKE_TYPE);

            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                    "/api/v1/users/me/likes", HttpMethod.GET, auth, LIST_TYPE);

            assertAll(
                    () -> assertThat(response.getBody().data()).hasSize(1),
                    () -> assertThat(response.getBody().data().get(0).id()).isEqualTo(productId)
            );
        }

        @DisplayName("좋아요 취소 후 조회하면, 목록에서 빠진다.")
        @Test
        void excludesUnliked() {
            HttpEntity<Void> auth = new HttpEntity<>(authHeaders("testid", "testPw1234"));
            testRestTemplate.exchange(likePath(), HttpMethod.POST, auth, LIKE_TYPE);
            testRestTemplate.exchange(likePath(), HttpMethod.DELETE, auth, LIKE_TYPE);

            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                    "/api/v1/users/me/likes", HttpMethod.GET, auth, LIST_TYPE);

            assertThat(response.getBody().data()).isEmpty();
        }
    }
}
