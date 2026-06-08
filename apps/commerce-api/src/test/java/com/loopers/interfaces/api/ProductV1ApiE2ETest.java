package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.interfaces.api.product.ProductV1Dto;
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
public class ProductV1ApiE2ETest {

    private static final String ENDPOINT_LIST = "/api/v1/products";
    private static final String ENDPOINT_DETAIL = "/api/v1/products/{productId}";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetDetail {

        @DisplayName("상품 상세를 조회하면 브랜드 정보와 좋아요 수가 함께 반환된다.")
        @Test
        void returnsProductWithBrandAndLikeCount() {
            // arrange
            BrandModel brand = brandRepository.save(new BrandModel("Nike", "스포츠"));
            ProductModel product = productRepository.save(new ProductModel(
                    brand.getId(), "운동화", "편한 신발", Money.of(50_000L), Quantity.of(10), "img.png"
            ));

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_DETAIL, HttpMethod.GET, null, type, product.getId());

            // assert
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            ProductV1Dto.ProductDetailResponse data = response.getBody().data();
            assertThat(data.id()).isEqualTo(product.getId());
            assertThat(data.brandId()).isEqualTo(brand.getId());
            assertThat(data.brandName()).isEqualTo("Nike");
            assertThat(data.price()).isEqualTo(50_000L);
            assertThat(data.likeCount()).isEqualTo(0);
            assertThat(data.isAvailable()).isTrue();
        }

        @DisplayName("존재하지 않는 productId 로 조회하면 404.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_DETAIL, HttpMethod.GET, null, type, 999_999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products (정렬)")
    @Nested
    class GetList {

        @DisplayName("price_asc 정렬은 가격 오름차순으로 반환한다.")
        @Test
        void returnsProducts_orderedByPriceAsc() {
            // arrange
            BrandModel brand = brandRepository.save(new BrandModel("Nike", null));
            productRepository.save(new ProductModel(brand.getId(), "A", null, Money.of(30_000L), Quantity.of(5), null));
            productRepository.save(new ProductModel(brand.getId(), "B", null, Money.of(10_000L), Quantity.of(5), null));
            productRepository.save(new ProductModel(brand.getId(), "C", null, Money.of(20_000L), Quantity.of(5), null));

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductListResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_LIST + "?sort=PRICE_ASC", HttpMethod.GET, null, type);

            // assert
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            var items = response.getBody().data().items();
            assertThat(items).hasSize(3);
            assertThat(items.get(0).name()).isEqualTo("B"); // 10000
            assertThat(items.get(1).name()).isEqualTo("C"); // 20000
            assertThat(items.get(2).name()).isEqualTo("A"); // 30000
        }

        @DisplayName("brandId 필터를 지정하면 해당 브랜드 상품만 반환한다.")
        @Test
        void returnsProducts_filteredByBrand() {
            // arrange
            BrandModel nike = brandRepository.save(new BrandModel("Nike", null));
            BrandModel adidas = brandRepository.save(new BrandModel("Adidas", null));
            productRepository.save(new ProductModel(nike.getId(), "N1", null, Money.of(10_000L), Quantity.of(5), null));
            productRepository.save(new ProductModel(nike.getId(), "N2", null, Money.of(20_000L), Quantity.of(5), null));
            productRepository.save(new ProductModel(adidas.getId(), "A1", null, Money.of(30_000L), Quantity.of(5), null));

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductListResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductListResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_LIST + "?brandId=" + nike.getId(), HttpMethod.GET, null, type);

            // assert
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            var items = response.getBody().data().items();
            assertThat(items).hasSize(2);
            assertThat(items).allMatch(item -> item.brandName().equals("Nike"));
        }
    }
}
