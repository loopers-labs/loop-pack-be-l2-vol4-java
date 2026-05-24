package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.ProductStockService;
import com.loopers.interfaces.api.ApiResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT_PRODUCT_DETAIL = "/api/v1/products/{productId}";

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductV1ApiE2ETest(
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

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("미삭제 상품 ID가 주어지면 200 OK와 브랜드 정보, 좋아요 수를 포함한 상품 정보를 반환한다.")
        @Test
        void returnsProductDetail_whenProductIdExists() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product product = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(product.getId(), 10);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = getProduct(product.getId());

            // assert
            ProductV1Dto.ProductResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.id()).isEqualTo(product.getId()),
                () -> assertThat(data.brand().id()).isEqualTo(brand.getId()),
                () -> assertThat(data.brand().name()).isEqualTo("애플"),
                () -> assertThat(data.brand().description()).isEqualTo("기술과 디자인으로 일상을 새롭게 만드는 브랜드"),
                () -> assertThat(data.name()).isEqualTo("아이폰 16 Pro"),
                () -> assertThat(data.description()).isEqualTo("강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰"),
                () -> assertThat(data.price()).isEqualTo(1_550_000L),
                () -> assertThat(data.likeCount()).isZero()
            );
        }
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
}
