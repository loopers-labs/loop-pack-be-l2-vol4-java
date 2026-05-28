package com.loopers.interfaces.api;

import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.fixture.UserFixture;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String USERS_URL    = "/api/v1/users";
    private static final String BRANDS_URL   = "/api/v1/admin/brands";
    private static final String PRODUCTS_URL = "/api/v1/admin/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        // 유저 등록
        ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> userResp = testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(UserFixture.createRequest()),
            new ParameterizedTypeReference<>() {}
        );
        assertThat(userResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        userId = userResp.getBody().data().id();

        // 브랜드 + 상품 생성
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> brandResp = testRestTemplate.exchange(
            BRANDS_URL, HttpMethod.POST,
            new HttpEntity<>(new BrandV1Dto.CreateRequest(BrandFixture.NAME, BrandFixture.DESCRIPTION)),
            new ParameterizedTypeReference<>() {}
        );
        UUID brandId = brandResp.getBody().data().id();

        ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> productResp = testRestTemplate.exchange(
            PRODUCTS_URL, HttpMethod.POST,
            new HttpEntity<>(new ProductV1Dto.CreateRequest(
                brandId, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE, ProductFixture.INITIAL_QUANTITY
            )),
            new ParameterizedTypeReference<>() {}
        );
        productId = productResp.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", UserFixture.LOGIN_ID);
        headers.set("X-Loopers-LoginPw", UserFixture.PASSWORD);
        return headers;
    }

    private String likeUrl(UUID pid) {
        return "/api/v1/products/" + pid + "/likes";
    }

    private String likesUrl(UUID uid) {
        return "/api/v1/users/" + uid + "/likes";
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class Like {

        @DisplayName("좋아요 등록 시, 200 + likeCount=1을 반환한다.")
        @Test
        void returnsLikeCount_whenLiked() {
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("중복 좋아요 시, 200 + likeCount=1 유지 (멱등).")
        @Test
        void returnsIdempotent_whenDuplicateLike() {
            testRestTemplate.exchange(likeUrl(productId), HttpMethod.POST, new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<>() {});

            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("인증 헤더 없이 요청 시, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenNoAuth() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.POST,
                null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 상품에 좋아요 시, 404를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                likeUrl(UUID.randomUUID()), HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class Unlike {

        @DisplayName("좋아요 취소 시, 200 + likeCount=0을 반환한다.")
        @Test
        void returnsLikeCount_whenUnliked() {
            testRestTemplate.exchange(likeUrl(productId), HttpMethod.POST, new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<>() {});

            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0L)
            );
        }

        @DisplayName("없는 좋아요 취소 시, 200 + likeCount=0 유지 (멱등).")
        @Test
        void returnsIdempotent_whenNotLiked() {
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response = testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0L)
            );
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    class GetLikeList {

        @DisplayName("본인 좋아요 목록 조회 시, 200 + 좋아요한 상품 목록을 반환한다.")
        @Test
        void returnsLikeList_whenOwnUser() {
            testRestTemplate.exchange(likeUrl(productId), HttpMethod.POST, new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<>() {});

            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = testRestTemplate.exchange(
                likesUrl(userId) + "?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(1)
            );
        }

        @DisplayName("타인의 좋아요 목록 조회 시, 404를 반환한다.")
        @Test
        void returnsNotFound_whenOtherUser() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                likesUrl(UUID.randomUUID()) + "?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
