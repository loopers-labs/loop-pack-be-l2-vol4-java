package com.loopers.interfaces.api.like;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.like.LikeApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.user.UserApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private static final String DEFAULT_LOGIN_ID = "testuser1";
    private static final String DEFAULT_PASSWORD = "Test1234!";

    private final TestRestTemplate testRestTemplate;
    private final BrandApplicationService brandApplicationService;
    private final ProductApplicationService productApplicationService;
    private final LikeApplicationService likeApplicationService;
    private final UserApplicationService userApplicationService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    LikeV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandApplicationService brandApplicationService,
            ProductApplicationService productApplicationService,
            LikeApplicationService likeApplicationService,
            UserApplicationService userApplicationService,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandApplicationService = brandApplicationService;
        this.productApplicationService = productApplicationService;
        this.likeApplicationService = likeApplicationService;
        this.userApplicationService = userApplicationService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private String createUser() {
        return userApplicationService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, "홍길동",
                LocalDate.of(1995, 1, 1), "test@test.com").id();
    }

    private ProductInfo createProduct() {
        BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
        return productApplicationService.createProduct(brand.id(), "에어맥스", "운동화", 100_000L, 10);
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, DEFAULT_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, DEFAULT_PASSWORD);
        return headers;
    }

    // ─────────────────────────────────────────────
    // POST /api/v1/products/{productId}/likes
    // ─────────────────────────────────────────────

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class AddLike {

        @DisplayName("인증 헤더 없이 요청하면 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            ProductInfo product = createProduct();

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/products/" + product.id() + "/likes",
                    HttpMethod.POST, HttpEntity.EMPTY, type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("유효한 요청이면 204 No Content를 반환한다.")
        @Test
        void returnsNoContent_whenRequestIsValid() {
            createUser();
            ProductInfo product = createProduct();

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/products/" + product.id() + "/likes",
                    HttpMethod.POST, new HttpEntity<>(userHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @DisplayName("이미 좋아요한 상품에 재등록하면 409를 반환한다.")
        @Test
        void returnsConflict_whenAlreadyLiked() {
            String userId = createUser();
            ProductInfo product = createProduct();
            likeApplicationService.addLike(userId, product.id());

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/products/" + product.id() + "/likes",
                    HttpMethod.POST, new HttpEntity<>(userHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("좋아요 취소 후 재등록하면 204를 반환한다.")
        @Test
        void returnsNoContent_whenRestored() {
            String userId = createUser();
            ProductInfo product = createProduct();
            likeApplicationService.addLike(userId, product.id());
            likeApplicationService.removeLike(userId, product.id());

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/products/" + product.id() + "/likes",
                    HttpMethod.POST, new HttpEntity<>(userHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    // ─────────────────────────────────────────────
    // DELETE /api/v1/products/{productId}/likes
    // ─────────────────────────────────────────────

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class RemoveLike {

        @DisplayName("좋아요한 상품을 취소하면 204 No Content를 반환한다.")
        @Test
        void returnsNoContent_whenLikeExists() {
            String userId = createUser();
            ProductInfo product = createProduct();
            likeApplicationService.addLike(userId, product.id());

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/products/" + product.id() + "/likes",
                    HttpMethod.DELETE, new HttpEntity<>(userHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @DisplayName("좋아요하지 않은 상품을 취소하면 404를 반환한다.")
        @Test
        void returnsNotFound_whenLikeNotExists() {
            createUser();
            ProductInfo product = createProduct();

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/products/" + product.id() + "/likes",
                    HttpMethod.DELETE, new HttpEntity<>(userHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api/v1/users/{userId}/likes
    // ─────────────────────────────────────────────

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    class GetLikedProducts {

        @DisplayName("내 좋아요 목록을 조회하면 200과 PLP 필드셋을 반환한다.")
        @Test
        void returnsLikedProducts_whenRequestIsValid() {
            String userId = createUser();
            ProductInfo product = createProduct();
            likeApplicationService.addLike(userId, product.id());

            ParameterizedTypeReference<ApiResponse<PageResult<LikeV1Dto.LikeResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<LikeV1Dto.LikeResponse>>> response =
                    testRestTemplate.exchange(
                            "/api/v1/users/" + userId + "/likes?page=0&size=20",
                            HttpMethod.GET, new HttpEntity<>(userHeaders()), type
                    );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(1);
            LikeV1Dto.LikeResponse item = response.getBody().data().content().get(0);
            assertThat(item.id()).isEqualTo(product.id());
            assertThat(item.name()).isEqualTo("에어맥스");
            assertThat(item.brandName()).isEqualTo("나이키");
            assertThat(item.price()).isEqualTo(100_000L);
            assertThat(item.likeCount()).isEqualTo(1L);
        }

        @DisplayName("타인의 좋아요 목록을 조회하면 403을 반환한다.")
        @Test
        void returnsForbidden_whenAccessingOtherUserLikes() {
            createUser();
            String otherUserId = userApplicationService.signup("otheruser1", "Other1234!", "김철수",
                    LocalDate.of(1990, 5, 15), "other@test.com").id();

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/users/" + otherUserId + "/likes?page=0&size=20",
                    HttpMethod.GET, new HttpEntity<>(userHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
