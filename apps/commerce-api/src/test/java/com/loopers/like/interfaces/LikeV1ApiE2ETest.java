package com.loopers.like.interfaces;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.product.application.ProductInfo;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.support.response.ApiResponse;
import com.loopers.user.domain.Gender;
import com.loopers.user.interfaces.UserV1Dto;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String LOGIN_ID = "user1";
    private static final String PASSWORD = "Pass123!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;

    @BeforeEach
    void setUp() {
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = testRestTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(new UserV1Dto.SignUpRequest(LOGIN_ID, PASSWORD, "홍길동", "test@example.com", "2000-01-01", Gender.MALE)),
            responseType
        );
        userId = response.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", PASSWORD);
        return headers;
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class AddLike {

        @DisplayName("정상 요청이면, 200 OK와 LikeResponse를 반환한다.")
        @Test
        void returnsLikeResponse_whenRequestIsValid() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            String url = "/api/v1/products/" + product.getId() + "/likes";

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().productId()).isEqualTo(product.getId())
            );
        }

        @DisplayName("이미 좋아요한 상품이면, 409 Conflict를 반환한다.")
        @Test
        void returnsConflict_whenAlreadyLiked() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            String url = "/api/v1/products/" + product.getId() + "/likes";
            testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(authHeaders()), Void.class);

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("존재하지 않는 productId이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response =
                testRestTemplate.exchange("/api/v1/products/999/likes", HttpMethod.POST, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증 헤더가 없으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenAuthHeaderIsMissing() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            String url = "/api/v1/products/" + product.getId() + "/likes";

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response =
                testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class CancelLike {

        @DisplayName("정상 요청이면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenRequestIsValid() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            String url = "/api/v1/products/" + product.getId() + "/likes";
            testRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(authHeaders()), Void.class);

            // act
            ResponseEntity<Void> response =
                testRestTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("좋아요하지 않은 상품이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenLikeNotExists() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange("/api/v1/products/999/likes", HttpMethod.DELETE, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    class GetLikedProducts {

        @DisplayName("좋아요한 상품이 있으면, 200 OK와 ProductResponse 목록을 반환한다.")
        @Test
        void returnsProductList_whenLikedProductsExist() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            testRestTemplate.exchange(
                "/api/v1/products/" + product.getId() + "/likes",
                HttpMethod.POST, new HttpEntity<>(authHeaders()), Void.class
            );

            // act
            ParameterizedTypeReference<ApiResponse<List<ProductInfo>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductInfo>>> response =
                testRestTemplate.exchange("/api/v1/users/" + userId + "/likes", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).id()).isEqualTo(product.getId())
            );
        }

        @DisplayName("좋아요한 상품이 없으면, 200 OK와 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikedProducts() {
            // act
            ParameterizedTypeReference<ApiResponse<List<ProductInfo>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductInfo>>> response =
                testRestTemplate.exchange("/api/v1/users/" + userId + "/likes", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("다른 유저의 좋아요 목록을 조회하면, 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenAccessingAnotherUserLikes() {
            // act
            ParameterizedTypeReference<ApiResponse<List<ProductInfo>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductInfo>>> response =
                testRestTemplate.exchange("/api/v1/users/99999/likes", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("브랜드가 있는 상품을 좋아요했을 때, 응답에 brandName이 포함된다.")
        @Test
        void returnsBrandName_whenLikedProductHasBrand() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, brand.getId()));
            testRestTemplate.exchange(
                "/api/v1/products/" + product.getId() + "/likes",
                HttpMethod.POST, new HttpEntity<>(authHeaders()), Void.class
            );

            // act
            ParameterizedTypeReference<ApiResponse<List<ProductInfo>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductInfo>>> response =
                testRestTemplate.exchange("/api/v1/users/" + userId + "/likes", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertThat(response.getBody().data().get(0).brandName()).isEqualTo("나이키");
        }
    }
}
