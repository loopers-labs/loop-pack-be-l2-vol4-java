package com.loopers.like.interfaces;

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

    private static final String ENDPOINT = "/api/v1/likes";
    private static final String LOGIN_ID = "user1";
    private static final String PASSWORD = "Pass123!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(new UserV1Dto.SignUpRequest(LOGIN_ID, PASSWORD, "홍길동", "test@example.com", "2000-01-01", Gender.MALE)),
            Void.class
        );
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

    @DisplayName("POST /api/v1/likes")
    @Nested
    class AddLike {

        @DisplayName("정상 요청이면, 200 OK와 LikeResponse를 반환한다.")
        @Test
        void returnsLikeResponse_whenRequestIsValid() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            LikeV1Dto.AddLikeRequest request = new LikeV1Dto.AddLikeRequest(product.getId());

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType);

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
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            LikeV1Dto.AddLikeRequest request = new LikeV1Dto.AddLikeRequest(product.getId());
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), Void.class);

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("존재하지 않는 productId이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // arrange
            LikeV1Dto.AddLikeRequest request = new LikeV1Dto.AddLikeRequest(999L);

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증 헤더가 없으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenAuthHeaderIsMissing() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            LikeV1Dto.AddLikeRequest request = new LikeV1Dto.AddLikeRequest(product.getId());

            // act
            ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<LikeV1Dto.LikeResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/likes/{productId}")
    @Nested
    class CancelLike {

        @DisplayName("정상 요청이면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenRequestIsValid() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(new LikeV1Dto.AddLikeRequest(product.getId()), authHeaders()), Void.class);

            // act
            ResponseEntity<Void> response =
                testRestTemplate.exchange(ENDPOINT + "/" + product.getId(), HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("좋아요하지 않은 상품이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenLikeNotExists() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT + "/999", HttpMethod.DELETE, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/likes/products")
    @Nested
    class GetLikedProducts {

        @DisplayName("좋아요한 상품이 있으면, 200 OK와 ProductResponse 목록을 반환한다.")
        @Test
        void returnsProductList_whenLikedProductsExist() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(new LikeV1Dto.AddLikeRequest(product.getId()), authHeaders()), Void.class);

            // act
            ParameterizedTypeReference<ApiResponse<List<ProductInfo>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductInfo>>> response =
                testRestTemplate.exchange(ENDPOINT + "/products", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

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
                testRestTemplate.exchange(ENDPOINT + "/products", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }
    }
}
