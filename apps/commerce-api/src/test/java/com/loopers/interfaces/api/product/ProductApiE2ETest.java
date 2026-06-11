package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.dto.ProductV1Response;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long product1Id;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        product1Id = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();
        Long product2Id = productRepository.save(new ProductModel(brand.getId(), "맨투맨", "심플", 30_000L)).getId();
        stockRepository.save(new StockModel(product1Id, 10));
        stockRepository.save(new StockModel(product2Id, 5));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품 목록을 조회하면 200 OK 를 반환한다")
    @Test
    void search_returns200() {
        ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
            "/api/v1/products", HttpMethod.GET, HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {}
        );

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
        );
    }

    @DisplayName("상품 상세를 조회하면 상품 정보(브랜드명·재고 여부 포함)를 반환한다")
    @Test
    void getProduct_returnsDetail() {
        ResponseEntity<ApiResponse<ProductV1Response>> response = restTemplate.exchange(
            "/api/v1/products/" + product1Id, HttpMethod.GET, HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {}
        );

        ProductV1Response data = response.getBody().data();
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(data.name()).isEqualTo("후드"),
            () -> assertThat(data.brandName()).isEqualTo("Loopers"),
            () -> assertThat(data.available()).isTrue()
        );
    }

    @DisplayName("존재하지 않는 상품을 조회하면 404 NOT_FOUND 를 반환한다")
    @Test
    void getProduct_returns404_whenNotFound() {
        ResponseEntity<ApiResponse<ProductV1Response>> response = restTemplate.exchange(
            "/api/v1/products/99999", HttpMethod.GET, HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {}
        );

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
        );
    }
}
