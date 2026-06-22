package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private ProductModel persistProduct() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "Just Do It"));
        return productJpaRepository.save(new ProductModel(brand.getId(), "에어맥스", "운동화", 1000L, 10));
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProductDetail {

        @DisplayName("존재하는 상품이면, 2xx 응답과 브랜드명·좋아요 수를 포함한 상세를 반환한다.")
        @Test
        void returnsDetailWithBrandAndLikeCount() {
            // arrange
            ProductModel product = persistProduct();

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(), HttpMethod.GET, null, responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().id()).isEqualTo(product.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(1000L),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(10),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0)
            );
        }

        @DisplayName("존재하지 않는 상품이면, 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/99999", HttpMethod.GET, null, responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
