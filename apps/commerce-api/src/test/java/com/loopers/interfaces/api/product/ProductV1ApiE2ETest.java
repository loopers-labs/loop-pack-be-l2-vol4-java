package com.loopers.interfaces.api.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

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

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("브랜드 필터, 가격 오름차순 정렬, 페이징을 적용한 상품 목록을 반환한다.")
        @Test
        void returnsPagedProducts_whenBrandSortAndPageAreProvided() {
            // arrange
            BrandModel nike = brandJpaRepository.save(new BrandModel("나이키", null, null));
            BrandModel adidas = brandJpaRepository.save(new BrandModel("아디다스", null, null));
            productJpaRepository.save(new ProductModel(nike.getId(), "비싼 신발", "설명", 20000L, 3));
            productJpaRepository.save(new ProductModel(adidas.getId(), "다른 브랜드 신발", "설명", 1000L, 2));
            productJpaRepository.save(new ProductModel(nike.getId(), "저렴한 신발", "설명", 10000L, 5));

            // act
            ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                "/api/v1/products?brandId=" + nike.getId() + "&sort=price&direction=asc&page=0&size=20",
                JsonNode.class
            );

            // assert
            JsonNode body = response.getBody();
            assertNotNull(body);
            JsonNode data = body.get("data");
            JsonNode content = data.get("content");
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(content).hasSize(2),
                () -> assertThat(content.get(0).get("name").asText()).isEqualTo("저렴한 신발"),
                () -> assertThat(content.get(0).get("brandName").asText()).isEqualTo("나이키"),
                () -> assertThat(content.get(0).get("likeCount").asInt()).isZero(),
                () -> assertThat(content.get(1).get("name").asText()).isEqualTo("비싼 신발"),
                () -> assertThat(data.get("page").asInt()).isZero(),
                () -> assertThat(data.get("size").asInt()).isEqualTo(20),
                () -> assertThat(data.get("totalElements").asLong()).isEqualTo(2),
                () -> assertThat(data.get("totalPages").asInt()).isEqualTo(1)
            );
        }

        @DisplayName("잘못된 정렬 값을 주면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenSortIsInvalid() {
            // act
            ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                "/api/v1/products?sort=unknown",
                JsonNode.class
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 ID를 주면, 상품 상세 정보를 반환한다.")
        @Test
        void returnsProductDetail_whenProductExists() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드", null));
            ProductModel product = productJpaRepository.save(
                new ProductModel(brand.getId(), "신발", "편한 신발", 10000L, 5)
            );

            // act
            ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(
                "/api/v1/products/" + product.getId(),
                JsonNode.class
            );

            // assert
            JsonNode body = response.getBody();
            assertNotNull(body);
            JsonNode data = body.get("data");
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(data.get("id").asLong()).isEqualTo(product.getId()),
                () -> assertThat(data.get("brandId").asLong()).isEqualTo(brand.getId()),
                () -> assertThat(data.get("brandName").asText()).isEqualTo("나이키"),
                () -> assertThat(data.get("likeCount").asInt()).isZero()
            );
        }
    }
}
