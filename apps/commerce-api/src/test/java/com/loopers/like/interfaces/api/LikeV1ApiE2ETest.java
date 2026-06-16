package com.loopers.like.interfaces.api;

import com.loopers.brand.application.BrandAdminService;
import com.loopers.brand.application.BrandCommand;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.application.ProductAdminService;
import com.loopers.product.application.ProductCommand;
import com.loopers.product.domain.ProductStatus;
import com.loopers.user.application.UserAccountService;
import com.loopers.user.application.UserCommand;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String LOGIN_ID = "loopers01";
    private static final String RAW_PASSWORD = "Passw0rd!";

    private final TestRestTemplate testRestTemplate;
    private final UserAccountService userAccountService;
    private final BrandAdminService brandAdminService;
    private final ProductAdminService productAdminService;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;
    private Long deletedProductId;
    private Long suspendedProductId;

    @Autowired
    public LikeV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            UserAccountService userAccountService,
            BrandAdminService brandAdminService,
            ProductAdminService productAdminService,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userAccountService = userAccountService;
        this.brandAdminService = brandAdminService;
        this.productAdminService = productAdminService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        userAccountService.signUp(new UserCommand.SignUp(
                LOGIN_ID, RAW_PASSWORD, "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        ));
        Long brandId = brandAdminService.create(new BrandCommand.Create("루퍼스", "설명", null)).id();
        productId = productAdminService.create(
                new ProductCommand.Create(brandId, "셔츠", "설명", 29_000L, null, 50)
        ).id();
        deletedProductId = productAdminService.create(
                new ProductCommand.Create(brandId, "삭제될 상품", "설명", 10_000L, null, 10)
        ).id();
        productAdminService.delete(deletedProductId);
        suspendedProductId = productAdminService.create(
                new ProductCommand.Create(brandId, "판매중지될 상품", "설명", 15_000L, null, 5)
        ).id();
        productAdminService.suspend(suspendedProductId);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    private ResponseEntity<ApiResponse<Void>> register(Long productId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
                "/api/v1/likes/products/" + productId, HttpMethod.POST, new HttpEntity<>(headers), type
        );
    }

    private ResponseEntity<ApiResponse<Void>> cancel(Long productId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
                "/api/v1/likes/products/" + productId, HttpMethod.DELETE, new HttpEntity<>(headers), type
        );
    }

    private ResponseEntity<ApiResponse<List<LikeV1Response.LikedProduct>>> getMyLikes(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<List<LikeV1Response.LikedProduct>>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
                "/api/v1/likes/products", HttpMethod.GET, new HttpEntity<>(headers), type
        );
    }

    @DisplayName("POST /api/v1/likes/products/{productId}")
    @Nested
    class Register {

        @Test
        @DisplayName("인증된 사용자가 활성 상품에 좋아요를 등록하면 200 을 받는다")
        void givenAuthenticatedUserAndActiveProduct_whenRegister_thenReturnsOk() {
            ResponseEntity<ApiResponse<Void>> response = register(productId, authHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("이미 좋아요한 상품에 다시 등록해도 200 을 받는다 (멱등)")
        void givenAlreadyLikedProduct_whenRegister_thenReturnsOk() {
            register(productId, authHeaders());

            ResponseEntity<ApiResponse<Void>> response = register(productId, authHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("취소된 좋아요를 재등록하면 200 을 받고 목록에 다시 노출된다")
        void givenCancelledLike_whenRegisterAgain_thenLikeIsRestored() {
            register(productId, authHeaders());
            cancel(productId, authHeaders());

            ResponseEntity<ApiResponse<Void>> response = register(productId, authHeaders());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(getMyLikes(authHeaders()).getBody().data())
                            .extracting(LikeV1Response.LikedProduct::productId)
                            .containsExactly(productId)
            );
        }

        @Test
        @DisplayName("삭제된 상품에 좋아요를 등록하면 404 NOT_FOUND 를 받는다")
        void givenDeletedProduct_whenRegister_thenThrowsNotFound() {
            ResponseEntity<ApiResponse<Void>> response = register(deletedProductId, authHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("판매중지 상품에 좋아요를 등록하면 404 NOT_FOUND 를 받는다")
        void givenSuspendedProduct_whenRegister_thenThrowsNotFound() {
            ResponseEntity<ApiResponse<Void>> response = register(suspendedProductId, authHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("인증 헤더가 없으면 401 UNAUTHORIZED 를 받는다")
        void givenMissingHeaders_whenRegister_thenThrowsUnauthorized() {
            ResponseEntity<ApiResponse<Void>> response = register(productId, new HttpHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api/v1/likes/products/{productId}")
    @Nested
    class Cancel {

        @Test
        @DisplayName("좋아요한 상품을 취소하면 200 을 받고 목록에서 제외된다")
        void givenLikedProduct_whenCancel_thenLikeIsRemovedFromList() {
            register(productId, authHeaders());

            ResponseEntity<ApiResponse<Void>> response = cancel(productId, authHeaders());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(getMyLikes(authHeaders()).getBody().data()).isEmpty()
            );
        }

        @Test
        @DisplayName("좋아요하지 않은 상품을 취소해도 200 을 받는다 (멱등)")
        void givenNotLikedProduct_whenCancel_thenReturnsOk() {
            ResponseEntity<ApiResponse<Void>> response = cancel(productId, authHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("인증 헤더가 없으면 401 UNAUTHORIZED 를 받는다")
        void givenMissingHeaders_whenCancel_thenThrowsUnauthorized() {
            ResponseEntity<ApiResponse<Void>> response = cancel(productId, new HttpHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/likes/products")
    @Nested
    class GetMyLikes {

        @Test
        @DisplayName("좋아요한 상품들이 목록으로 반환된다")
        void givenLikedProducts_whenGetMyLikes_thenReturnsList() {
            register(productId, authHeaders());

            ResponseEntity<ApiResponse<List<LikeV1Response.LikedProduct>>> response = getMyLikes(authHeaders());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data())
                            .extracting(LikeV1Response.LikedProduct::productId)
                            .containsExactly(productId)
            );
        }

        @Test
        @DisplayName("좋아요한 상품이 판매중지되면 목록에 status=SUSPENDED 로 남는다 (찜 보존)")
        void givenLikedProductBecomesSuspended_whenGetMyLikes_thenStillListedAsSuspended() {
            register(productId, authHeaders());
            productAdminService.suspend(productId);

            ResponseEntity<ApiResponse<List<LikeV1Response.LikedProduct>>> response = getMyLikes(authHeaders());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data())
                            .singleElement()
                            .satisfies(p -> {
                                assertThat(p.productId()).isEqualTo(productId);
                                assertThat(p.status()).isEqualTo(ProductStatus.SUSPENDED);
                            })
            );
        }

        @Test
        @DisplayName("좋아요한 상품이 없으면 빈 리스트를 반환한다")
        void givenNoLikes_whenGetMyLikes_thenReturnsEmptyList() {
            ResponseEntity<ApiResponse<List<LikeV1Response.LikedProduct>>> response = getMyLikes(authHeaders());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @Test
        @DisplayName("인증 헤더가 없으면 401 UNAUTHORIZED 를 받는다")
        void givenMissingHeaders_whenGetMyLikes_thenThrowsUnauthorized() {
            ResponseEntity<ApiResponse<List<LikeV1Response.LikedProduct>>> response = getMyLikes(new HttpHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
