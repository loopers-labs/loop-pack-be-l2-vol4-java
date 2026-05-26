package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.money.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/users";
    private static final Function<Long, String> ENDPOINT_LIKE =
        productId -> "/api/v1/products/" + productId + "/likes";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final LikeJpaRepository likeJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public LikeV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        LikeJpaRepository likeJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.likeJpaRepository = likeJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long signupAndGetUserId(String loginId, String password) {
        UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
            loginId, password, "김민우", LocalDate.of(1990, 1, 1), loginId + "@example.com"
        );
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                new HttpEntity<>(request), responseType);
        return response.getBody().data().id();
    }

    private Product saveProduct() {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        return productJpaRepository.save(
            new Product("에어맥스", "편한 러닝화",
                new Money(BigDecimal.valueOf(100000)), new Stock(10), brand.getId())
        );
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class Like_ {
        @DisplayName("유효한 인증 헤더로 좋아요 등록 시, product_like 행이 생성되고 Product.likeCount가 1 증가한다.")
        @Test
        void createsLikeAndIncreasesCount_whenAuthenticated() {
            // arrange
            signupAndGetUserId("minwoo01", "Passw0rd!");
            Product product = saveProduct();
            HttpHeaders headers = authHeaders("minwoo01", "Passw0rd!");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_LIKE.apply(product.getId()),
                    HttpMethod.POST, new HttpEntity<>(headers), responseType);

            // assert
            List<Like> all = likeJpaRepository.findAll();
            Product reloaded = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(all).hasSize(1),
                () -> assertThat(all.get(0).getProductId()).isEqualTo(product.getId()),
                () -> assertThat(reloaded.getLikeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenHeadersAreMissing() {
            // arrange
            Product product = saveProduct();

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_LIKE.apply(product.getId()),
                    HttpMethod.POST, new HttpEntity<>(null), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class Unlike {
        @DisplayName("활성 좋아요를 취소하면, 행이 소프트 삭제되고 Product.likeCount가 1 감소한다.")
        @Test
        void softDeletesAndDecreasesCount_whenActiveLikeIsCanceled() {
            // arrange
            signupAndGetUserId("minwoo01", "Passw0rd!");
            Product product = saveProduct();
            HttpHeaders headers = authHeaders("minwoo01", "Passw0rd!");
            ParameterizedTypeReference<ApiResponse<Object>> responseType =
                new ParameterizedTypeReference<>() {};
            // 먼저 좋아요 등록
            testRestTemplate.exchange(ENDPOINT_LIKE.apply(product.getId()),
                HttpMethod.POST, new HttpEntity<>(headers), responseType);

            // act
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_LIKE.apply(product.getId()),
                    HttpMethod.DELETE, new HttpEntity<>(headers), responseType);

            // assert
            List<Like> all = likeJpaRepository.findAll();
            Product reloaded = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(all).hasSize(1),
                () -> assertThat(all.get(0).getDeletedAt()).isNotNull(),
                () -> assertThat(reloaded.getLikeCount()).isEqualTo(0L)
            );
        }
    }
}
