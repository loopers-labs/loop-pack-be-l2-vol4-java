package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private BrandModel brand;
    private ProductModel product;

    @BeforeEach
    void setUp() {
        brand = brandRepository.save(new BrandModel("테스트브랜드"));
        product = productRepository.save(new ProductModel(brand.getId(), new ProductName("테스트상품")));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("200 OK와 상품 목록을 반환한다.")
        @Test
        void returnsProductList() {
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange("/api/v1/products", HttpMethod.GET, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).hasSize(1);
            assertThat(response.getBody().data().content().get(0).name()).isEqualTo("테스트상품");
        }

        @DisplayName("brandId로 필터링하면, 해당 브랜드 상품만 반환한다.")
        @Test
        void returnsFilteredList_whenBrandIdProvided() {
            BrandModel otherBrand = brandRepository.save(new BrandModel("다른브랜드"));
            productRepository.save(new ProductModel(otherBrand.getId(), new ProductName("다른상품")));
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange("/api/v1/products?brandId=" + brand.getId(), HttpMethod.GET, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).hasSize(1);
            assertThat(response.getBody().data().content().get(0).brandId()).isEqualTo(brand.getId());
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("상품이 존재하면, 200 OK와 상품 정보를 반환한다.")
        @Test
        void returnsProduct_whenProductExists() {
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange("/api/v1/products/" + product.getId(), HttpMethod.GET, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(product.getId());
            assertThat(response.getBody().data().name()).isEqualTo("테스트상품");
            assertThat(response.getBody().data().brandName()).isEqualTo("테스트브랜드");
        }

        @DisplayName("상품이 존재하지 않으면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange("/api/v1/products/999", HttpMethod.GET, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
