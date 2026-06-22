package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductLikeViewModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductLikeViewJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.interfaces.api.product.ProductDto;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductLikeViewJpaRepository productLikeViewJpaRepository;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("재고가 있는 상품을 조회하면, stockQuantity가 응답에 포함된다.")
        @Test
        void returnsStockQuantity_whenProductExists() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키"));
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, brand.getId()));
            productLikeViewJpaRepository.save(new ProductLikeViewModel(product.getId()));
            stockJpaRepository.save(new StockModel(product.getId(), 10));

            // act
            ResponseEntity<ApiResponse<ProductDto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().stockQuantity()).isEqualTo(10);
        }

        @DisplayName("재고가 0인 상품을 조회하면, stockQuantity가 0으로 반환된다.")
        @Test
        void returnsZeroStock_whenProductIsOutOfStock() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키"));
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, brand.getId()));
            productLikeViewJpaRepository.save(new ProductLikeViewModel(product.getId()));
            stockJpaRepository.save(new StockModel(product.getId(), 0));

            // act
            ResponseEntity<ApiResponse<ProductDto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().stockQuantity()).isZero();
        }
    }
}
