package com.loopers.product.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.application.ProductDisplayStatus;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";
    private static final Long BRAND_ID = 1L;

    private final TestRestTemplate testRestTemplate;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            ProductRepository productRepository,
            ProductStockRepository productStockRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.productRepository = productRepository;
        this.productStockRepository = productStockRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product saveOnSale(String name, long price, int stock) {
        Product product = productRepository.save(Product.create(BRAND_ID, name, "설명", price, "https://cdn/" + name + ".png"));
        productStockRepository.save(ProductStock.create(product.getId(), stock));
        return product;
    }

    private Product saveSuspended(String name, long price, int stock) {
        Product product = Product.create(BRAND_ID, name, "설명", price, null);
        product.suspend();
        product = productRepository.save(product);
        productStockRepository.save(ProductStock.create(product.getId(), stock));
        return product;
    }

    private ResponseEntity<ApiResponse<ProductV1Response.Detail>> getById(Long productId) {
        ParameterizedTypeReference<ApiResponse<ProductV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT + "/" + productId, HttpMethod.GET, null, type);
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetOne {

        @Test
        @DisplayName("판매중 상품을 조회하면 200 과 displayStatus=ON_SALE 및 likeCount 를 반환한다")
        void givenOnSaleProduct_whenGet_thenReturnsDetail() {
            Product product = saveOnSale("셔츠", 29_000L, 10);

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = getById(product.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("셔츠"),
                    () -> assertThat(response.getBody().data().displayStatus()).isEqualTo(ProductDisplayStatus.ON_SALE),
                    () -> assertThat(response.getBody().data().likeCount()).isZero()
            );
        }

        @Test
        @DisplayName("재고가 0 인 판매중 상품은 displayStatus=SOLD_OUT 으로 반환한다")
        void givenZeroStock_whenGet_thenSoldOut() {
            Product product = saveOnSale("품절셔츠", 29_000L, 0);

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = getById(product.getId());

            assertThat(response.getBody().data().displayStatus()).isEqualTo(ProductDisplayStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("판매중지 상품은 사용자 조회에서 404 NOT_FOUND 이다")
        void givenSuspendedProduct_whenGet_thenNotFound() {
            Product product = saveSuspended("판매중지", 29_000L, 10);

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = getById(product.getId());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 productId 는 404 NOT_FOUND 이다")
        void givenNonExisting_whenGet_thenNotFound() {
            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = getById(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetAll {

        @Test
        @DisplayName("판매중 상품만 반환하고 판매중지 상품은 제외한다")
        void givenMixedProducts_whenGetAll_thenReturnsOnlyOnSale() {
            saveOnSale("A", 1000L, 5);
            saveOnSale("B", 2000L, 5);
            saveSuspended("판매중지", 3000L, 5);

            ParameterizedTypeReference<ApiResponse<List<ProductV1Response.Detail>>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductV1Response.Detail>>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.GET, null, type);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data())
                            .extracting(ProductV1Response.Detail::name)
                            .containsExactlyInAnyOrder("A", "B")
            );
        }

        @Test
        @DisplayName("sort=PRICE_ASC 로 가격 오름차순 정렬해 반환한다")
        void givenPriceAsc_whenGetAll_thenOrdered() {
            saveOnSale("비싼것", 50_000L, 5);
            saveOnSale("싼것", 10_000L, 5);

            ParameterizedTypeReference<ApiResponse<List<ProductV1Response.Detail>>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<ProductV1Response.Detail>>> response =
                    testRestTemplate.exchange(ENDPOINT + "?sort=PRICE_ASC", HttpMethod.GET, null, type);

            assertThat(response.getBody().data())
                    .extracting(ProductV1Response.Detail::name)
                    .containsExactly("싼것", "비싼것");
        }
    }
}
