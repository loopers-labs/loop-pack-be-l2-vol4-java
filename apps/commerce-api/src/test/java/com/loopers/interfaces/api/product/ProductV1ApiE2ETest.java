package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String BASE_URL = "/api/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel saveBrand(String name) {
        return brandRepository.save(new BrandModel(name));
    }

    private ProductModel saveProduct(Long brandId, String name, BigDecimal price) {
        return productRepository.save(new ProductModel(brandId, name, price));
    }

    private void saveStock(Long productId, Long quantity) {
        stockRepository.save(new StockModel(productId, quantity));
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 ID로 조회하면 상품 정보를 응답으로 반환한다.")
        @Test
        void returnsProductResponse_whenProductExists() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel product = saveProduct(brand.getId(), "에어맥스", BigDecimal.valueOf(150000));
            saveStock(product.getId(), 10L);

            // when
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                testRestTemplate.exchange(BASE_URL + "/" + product.getId(), HttpMethod.GET, null, responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(product.getId()),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("Nike"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(BigDecimal.valueOf(150000)),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0L),
                () -> assertThat(response.getBody().data().inStock()).isTrue()
            );
        }

        @DisplayName("존재하지 않는 상품 ID로 조회하면 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // when
            ResponseEntity<Void> response =
                testRestTemplate.exchange(BASE_URL + "/999", HttpMethod.GET, null, Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetAllProducts {

        @DisplayName("상품 목록을 조회하면 전체 상품 목록을 응답으로 반환한다.")
        @Test
        void returnsAllProducts_whenProductsExist() {
            // given
            BrandModel brand = saveBrand("Adidas");
            ProductModel productA = saveProduct(brand.getId(), "울트라부스트", BigDecimal.valueOf(200000));
            ProductModel productB = saveProduct(brand.getId(), "스탠스미스", BigDecimal.valueOf(100000));
            saveStock(productA.getId(), 5L);
            saveStock(productB.getId(), 3L);

            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(BASE_URL, HttpMethod.GET, null, responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                () -> assertThat(response.getBody().data().content()).allMatch(p -> p.brandName().equals("Adidas"))
            );
        }

        @DisplayName("상품이 없으면 빈 목록을 응답으로 반환한다.")
        @Test
        void returnsEmptyList_whenNoProductsExist() {
            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(BASE_URL, HttpMethod.GET, null, responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).isEmpty(),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }

        @DisplayName("brandId로 필터링하면 해당 브랜드의 상품만 응답으로 반환한다.")
        @Test
        void returnsFilteredProducts_whenBrandIdIsProvided() {
            // given
            BrandModel brandA = saveBrand("Nike");
            BrandModel brandB = saveBrand("Adidas");
            ProductModel productA = saveProduct(brandA.getId(), "나이키 상품", BigDecimal.valueOf(100000));
            saveProduct(brandB.getId(), "아디다스 상품", BigDecimal.valueOf(200000));
            saveStock(productA.getId(), 5L);

            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(BASE_URL + "?brandId=" + brandA.getId(), HttpMethod.GET, null, responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1),
                () -> assertThat(response.getBody().data().content().get(0).brandName()).isEqualTo("Nike")
            );
        }

        @DisplayName("존재하지 않는 brandId로 필터링하면 빈 목록을 응답으로 반환한다.")
        @Test
        void returnsEmptyList_whenBrandIdDoesNotExist() {
            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(BASE_URL + "?brandId=999", HttpMethod.GET, null, responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).isEmpty(),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }

        @DisplayName("page와 size 파라미터로 페이지네이션이 동작한다.")
        @Test
        void returnsPaginatedProducts_whenPageAndSizeAreProvided() {
            // given
            BrandModel brand = saveBrand("Nike");
            for (int i = 1; i <= 5; i++) {
                ProductModel product = saveProduct(brand.getId(), "상품" + i, BigDecimal.valueOf(i * 10000));
                saveStock(product.getId(), 10L);
            }

            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(BASE_URL + "?page=0&size=3", HttpMethod.GET, null, responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).hasSize(3),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(5),
                () -> assertThat(response.getBody().data().totalPages()).isEqualTo(2)
            );
        }
    }
}
