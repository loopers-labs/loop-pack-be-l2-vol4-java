package com.loopers.interfaces.api.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.ProductStockService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_PRODUCT_DETAIL = "/api/v1/products/{productId}";
    private static final String ENDPOINT_PRODUCT_LIKES = "/api/v1/products/{productId}/likes";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Loopers!2026";

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    LikeV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandService brandService,
        ProductService productService,
        ProductStockService productStockService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandService = brandService;
        this.productService = productService;
        this.productStockService = productStockService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class LikeProduct {

        @DisplayName("인증 사용자와 미삭제 상품 ID가 주어지면 200 OK를 반환하고, 상품 좋아요 수가 1 증가한다.")
        @Test
        void likesProduct_whenAuthenticatedUserAndProductIdExist() {
            // arrange
            signUpUser();
            Product product = createProduct();

            // act
            ResponseEntity<ApiResponse<Object>> likeResponse = likeProduct(product.getId(), authHeaders());
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> detailResponse = getProduct(product.getId());

            // assert
            assertAll(
                () -> assertThat(likeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(detailResponse.getBody().data().likeCount()).isEqualTo(1)
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요를 등록해도 200 OK를 반환하고, 상품 좋아요 수를 중복 증가시키지 않는다.")
        @Test
        void keepsLikeCount_whenProductIsAlreadyLiked() {
            // arrange
            signUpUser();
            Product product = createProduct();
            likeProduct(product.getId(), authHeaders());

            // act
            ResponseEntity<ApiResponse<Object>> likeResponse = likeProduct(product.getId(), authHeaders());
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> detailResponse = getProduct(product.getId());

            // assert
            assertAll(
                () -> assertThat(likeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(detailResponse.getBody().data().likeCount()).isEqualTo(1)
            );
        }
    }

    private Product createProduct() {
        Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
        Product product = productService.createProduct(
            brand.getId(),
            "아이폰 16 Pro",
            "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
            1_550_000L
        );
        productStockService.createProductStock(product.getId(), 10);
        return product;
    }

    private void signUpUser() {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
            LOGIN_ID,
            PASSWORD,
            "김성호",
            LocalDate.of(1993, 11, 3),
            "loopers@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_USERS, request, ApiResponse.class);
    }

    private ResponseEntity<ApiResponse<Object>> likeProduct(Long productId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PRODUCT_LIKES,
            HttpMethod.POST,
            new HttpEntity<>(headers),
            responseType,
            productId
        );
    }

    private ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> getProduct(Long productId) {
        ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PRODUCT_DETAIL,
            HttpMethod.GET,
            null,
            responseType,
            productId
        );
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, PASSWORD);
        return headers;
    }
}
