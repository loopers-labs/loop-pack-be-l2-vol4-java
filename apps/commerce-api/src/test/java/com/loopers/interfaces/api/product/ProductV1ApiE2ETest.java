package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";
    private static final String ENDPOINT_ADMIN_PRODUCTS = "/api-admin/v1/products";
    private static final String ENDPOINT_SIGNUP = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProduct {
        @DisplayName("존재하는 상품 ID이면, 브랜드 정보와 좋아요 수를 포함한 상품 상세를 반환한다.")
        @Test
        void returnsProductWithBrandAndLikeCount_whenProductExists() {
            // arrange
            BrandModel brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductModel product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/" + product.getId(),
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    productResponseType()
                );

            // assert
            ProductV1Dto.ProductResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.id()).isEqualTo(product.getId()),
                () -> assertThat(data.name()).isEqualTo("니트"),
                () -> assertThat(data.brand().id()).isEqualTo(brand.getId()),
                () -> assertThat(data.brand().name()).isEqualTo("Loopers"),
                () -> assertThat(data.likeCount()).isZero()
            );
        }
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class CreateProduct {
        @DisplayName("필수 요청 값이 비어있으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenRequiredFieldsAreMissing() {
            // arrange
            ProductV1Dto.CreateProductRequest request = new ProductV1Dto.CreateProductRequest(
                null,
                "",
                "",
                -1L,
                -1
            );

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ADMIN_PRODUCTS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, adminHeaders()),
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {
        @DisplayName("price_asc 정렬 조건이면, 상품 목록을 낮은 가격순으로 반환한다.")
        @Test
        void returnsProductsSortedByPriceAsc_whenSortIsPriceAsc() {
            // arrange
            BrandModel brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductModel expensiveProduct = saveProduct(brand.getId(), "코트", "따뜻한 코트", 90_000L, 10);
            ProductModel cheapProduct = saveProduct(brand.getId(), "양말", "부드러운 양말", 5_000L, 10);

            // act
            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?sort=price_asc",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    productListResponseType()
                );

            // assert
            List<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data).hasSize(2),
                () -> assertThat(data.get(0).id()).isEqualTo(cheapProduct.getId()),
                () -> assertThat(data.get(1).id()).isEqualTo(expensiveProduct.getId())
            );
        }

        @DisplayName("지원하지 않는 정렬 조건이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenSortIsNotSupported() {
            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?sort=unknown",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class LikeProduct {
        @DisplayName("인증된 회원이 상품에 좋아요를 누르면, 상품 좋아요 수가 증가한다.")
        @Test
        void increasesLikeCount_whenUserLikesProduct() {
            // arrange
            signup("user1234", "abc123!?");
            BrandModel brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductModel product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);

            // act
            ResponseEntity<ApiResponse<Void>> likeResponse =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/" + product.getId() + "/likes",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders("user1234", "abc123!?")),
                    voidResponseType()
                );

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/" + product.getId(),
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    productResponseType()
                );

            // assert
            assertAll(
                () -> assertThat(likeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(productResponse.getBody().data().likeCount()).isEqualTo(1)
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요를 누르면, 좋아요 수를 중복 증가시키지 않는다.")
        @Test
        void keepsLikeCount_whenUserLikesSameProductAgain() {
            // arrange
            signup("user1234", "abc123!?");
            BrandModel brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductModel product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);
            HttpEntity<Void> authenticatedRequest = new HttpEntity<>(authHeaders("user1234", "abc123!?"));

            // act
            testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + product.getId() + "/likes",
                HttpMethod.POST,
                authenticatedRequest,
                voidResponseType()
            );
            testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + product.getId() + "/likes",
                HttpMethod.POST,
                authenticatedRequest,
                voidResponseType()
            );

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/" + product.getId(),
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    productResponseType()
                );

            // assert
            assertThat(productResponse.getBody().data().likeCount()).isEqualTo(1);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenCredentialHeaderIsMissing() {
            // arrange
            BrandModel brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductModel product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/" + product.getId() + "/likes",
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class UnlikeProduct {
        @DisplayName("좋아요한 상품의 좋아요를 취소하면, 상품 좋아요 수가 감소한다.")
        @Test
        void decreasesLikeCount_whenUserUnlikesProduct() {
            // arrange
            signup("user1234", "abc123!?");
            BrandModel brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductModel product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);
            HttpEntity<Void> authenticatedRequest = new HttpEntity<>(authHeaders("user1234", "abc123!?"));
            testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + product.getId() + "/likes",
                HttpMethod.POST,
                authenticatedRequest,
                voidResponseType()
            );

            // act
            ResponseEntity<ApiResponse<Void>> unlikeResponse =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/" + product.getId() + "/likes",
                    HttpMethod.DELETE,
                    authenticatedRequest,
                    voidResponseType()
                );

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> productResponse =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/" + product.getId(),
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    productResponseType()
                );

            // assert
            assertAll(
                () -> assertThat(unlikeResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(productResponse.getBody().data().likeCount()).isZero()
            );
        }
    }

    private BrandModel saveBrand(String name, String description) {
        return brandJpaRepository.save(new BrandModel(name, description));
    }

    private ProductModel saveProduct(Long brandId, String name, String description, Long price, Integer stock) {
        return productJpaRepository.save(new ProductModel(brandId, name, description, price, stock));
    }

    private void signup(String loginId, String password) {
        UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
            loginId,
            password,
            "홍길동",
            LocalDate.of(1990, 1, 15),
            loginId + "@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_SIGNUP, request, String.class);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> productResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductResponse>>> productListResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<Void>> voidResponseType() {
        return new ParameterizedTypeReference<>() {};
    }
}
