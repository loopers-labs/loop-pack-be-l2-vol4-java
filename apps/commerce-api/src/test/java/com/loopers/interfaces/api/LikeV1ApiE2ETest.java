package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @Autowired
    public LikeV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        userJpaRepository.save(new UserModel("tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M));
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "Just Do It"));
        ProductModel product = productJpaRepository.save(new ProductModel(brand.getId(), "에어맥스", "운동화", 1000L, 10));
        this.productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.HEADER_LOGIN_ID, "tester01");
        headers.set(AuthHeaders.HEADER_LOGIN_PW, "Password1!");
        return headers;
    }

    private ResponseEntity<ApiResponse<Object>> exchangeLike(HttpMethod method) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api/v1/products/" + productId + "/likes", method, new HttpEntity<>(authHeaders()), responseType
        );
    }

    private int currentLikeCount() {
        return productJpaRepository.findById(productId).orElseThrow().getLikeCount();
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class Like {

        @DisplayName("좋아요를 등록하면, 2xx 응답과 함께 상품의 좋아요 수가 1 증가한다.")
        @Test
        void registersLike() {
            // act
            ResponseEntity<ApiResponse<Object>> response = exchangeLike(HttpMethod.POST);

            // assert
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(currentLikeCount()).isEqualTo(1);
        }

        @DisplayName("같은 상품에 두 번 좋아요해도, 좋아요 수는 1 로 유지된다. (멱등)")
        @Test
        void isIdempotent() {
            // act
            exchangeLike(HttpMethod.POST);
            exchangeLike(HttpMethod.POST);

            // assert
            assertThat(currentLikeCount()).isEqualTo(1);
        }

        @DisplayName("인증 헤더가 없으면, 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenNoAuthHeader() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes", HttpMethod.POST, null, responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class Unlike {

        @DisplayName("좋아요를 취소하면, 2xx 응답과 함께 상품의 좋아요 수가 감소한다.")
        @Test
        void cancelsLike() {
            // arrange
            exchangeLike(HttpMethod.POST); // likeCount = 1

            // act
            ResponseEntity<ApiResponse<Object>> response = exchangeLike(HttpMethod.DELETE);

            // assert
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(currentLikeCount()).isEqualTo(0);
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도, 2xx 응답이고 좋아요 수는 0 으로 유지된다. (멱등)")
        @Test
        void isIdempotent() {
            // act
            ResponseEntity<ApiResponse<Object>> response = exchangeLike(HttpMethod.DELETE);

            // assert
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(currentLikeCount()).isEqualTo(0);
        }
    }
}
