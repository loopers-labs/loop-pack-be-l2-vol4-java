package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.product.ProductV1Dto;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String USER_PRODUCT_ENDPOINT  = "/api/v1/products";
    private static final String ADMIN_PRODUCT_ENDPOINT = "/api-admin/v1/products";
    private static final String RAW_PASSWORD = "Password1!";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private LikeJpaRepository likeJpaRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private BrandModel savedBrand;
    private UserModel testUser;

    @BeforeEach
    void setUp() {
        savedBrand = brandJpaRepository.save(new BrandModel("Nike", "스포츠 브랜드"));
        testUser = userJpaRepository.save(new UserModel(
            "testuser", passwordEncoder.encode(RAW_PASSWORD),
            "테스터", LocalDate.of(1990, 1, 15), "test@example.com"
        ));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel saveProduct(BrandModel brand, String name, int price, int stock) {
        ProductModel product = productJpaRepository.save(new ProductModel(brand, name, price));
        stockJpaRepository.save(new StockModel(product, stock));
        return product;
    }

    private HttpHeaders userAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", testUser.getLoginId());
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    // ── 사용자 API ───────────────────────────────────────────────────────────

    @DisplayName("GET /api/v1/products/{productId} 요청 시,")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 조회 시 200과 상품 정보가 반환된다.")
        @Test
        void returnsProduct_whenProductExists() {
            // arrange
            ProductModel saved = saveProduct(savedBrand, "나이키 에어맥스", 150_000, 50);

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                USER_PRODUCT_ENDPOINT + "/" + saved.getId(),
                HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(saved.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(150_000),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("Nike"),
                () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(50),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0)
            );
        }

        @DisplayName("존재하지 않는 상품 조회 시 404 NOT_FOUND가 반환된다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                USER_PRODUCT_ENDPOINT + "/999",
                HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products 요청 시,")
    @Nested
    class GetProducts {

        @DisplayName("활성 상품 목록이 페이지로 반환된다.")
        @Test
        void returnsActivePage_whenProductsExist() {
            // arrange
            saveProduct(savedBrand, "나이키 에어맥스", 150_000, 50);
            saveProduct(savedBrand, "나이키 조던", 200_000, 30);

            // act
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(USER_PRODUCT_ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(userAuthHeaders()), type);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }

        @DisplayName("brandId 필터 적용 시 해당 브랜드 상품만 반환된다.")
        @Test
        void returnsFilteredProducts_whenBrandIdProvided() {
            // arrange
            BrandModel another = brandJpaRepository.save(new BrandModel("Adidas", "독일 브랜드"));
            saveProduct(savedBrand, "나이키 에어맥스", 150_000, 50);
            saveProduct(another, "아디다스 삼바", 120_000, 40);

            // act
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(
                    USER_PRODUCT_ENDPOINT + "?brandId=" + savedBrand.getId(),
                    HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), type
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1),
                () -> assertThat(response.getBody().data().content().get(0).brandName()).isEqualTo("Nike")
            );
        }
    }

    // ── 어드민 API ───────────────────────────────────────────────────────────

    @DisplayName("POST /api-admin/v1/products 요청 시,")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 상품 등록 시 200과 등록된 상품 정보가 반환된다.")
        @Test
        void returnsProduct_whenValidRequestProvided() {
            // arrange
            Map<String, Object> request = Map.of(
                "brandId", savedBrand.getId(),
                "name", "나이키 에어맥스",
                "price", 150_000,
                "initialStock", 100
            );

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ADMIN_PRODUCT_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), type
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("Nike"),
                () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(100)
            );
        }

        @DisplayName("상품명 없이 등록 시 400 BAD_REQUEST가 반환된다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            Map<String, Object> request = Map.of(
                "brandId", savedBrand.getId(),
                "name", "",
                "price", 150_000,
                "initialStock", 10
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ADMIN_PRODUCT_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 브랜드로 등록 시 400 BAD_REQUEST가 반환된다.")
        @Test
        void returnsBadRequest_whenBrandDoesNotExist() {
            // arrange
            Map<String, Object> request = Map.of(
                "brandId", 999L,
                "name", "상품명",
                "price", 10_000,
                "initialStock", 10
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ADMIN_PRODUCT_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId} 요청 시,")
    @Nested
    class UpdateProduct {

        @DisplayName("유효한 정보로 수정 시 200과 수정된 상품 정보가 반환된다.")
        @Test
        void returnsUpdatedProduct_whenValidRequestProvided() {
            // arrange
            ProductModel saved = saveProduct(savedBrand, "나이키 에어맥스", 150_000, 10);
            Map<String, Object> request = Map.of("name", "나이키 조던", "price", 200_000);

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ADMIN_PRODUCT_ENDPOINT + "/" + saved.getId(),
                HttpMethod.PUT, new HttpEntity<>(request), type
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키 조던"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(200_000),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("Nike") // 브랜드 불변
            );
        }

        @DisplayName("존재하지 않는 상품 수정 시 404 NOT_FOUND가 반환된다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // arrange
            Map<String, Object> request = Map.of("name", "상품명", "price", 10_000);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ADMIN_PRODUCT_ENDPOINT + "/999",
                HttpMethod.PUT, new HttpEntity<>(request), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId} 요청 시,")
    @Nested
    class DeleteProduct {

        @DisplayName("상품 삭제 후 사용자 API로 조회 시 404 NOT_FOUND가 반환된다.")
        @Test
        void returnsNotFound_afterProductDeleted() {
            // arrange
            ProductModel saved = saveProduct(savedBrand, "나이키 에어맥스", 150_000, 10);

            // act - 삭제
            ParameterizedTypeReference<ApiResponse<Object>> deleteType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> deleteResponse = testRestTemplate.exchange(
                ADMIN_PRODUCT_ENDPOINT + "/" + saved.getId(),
                HttpMethod.DELETE, null, deleteType
            );
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // assert - 이후 조회 시 NOT_FOUND
            ParameterizedTypeReference<ApiResponse<Void>> getType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> getResponse = testRestTemplate.exchange(
                USER_PRODUCT_ENDPOINT + "/" + saved.getId(),
                HttpMethod.GET, new HttpEntity<>(userAuthHeaders()), getType
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 상품 삭제 시 404 NOT_FOUND가 반환된다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ADMIN_PRODUCT_ENDPOINT + "/999",
                HttpMethod.DELETE, null, type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    record PageResponse<T>(List<T> content, long totalElements, int totalPages, int size, int number) {}
}
