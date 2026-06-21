package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.wishlist.WishlistModel;
import com.loopers.domain.wishlist.WishlistRepository;
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
    @Autowired private WishlistRepository wishlistRepository;
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

        @DisplayName("sort=LATEST를 주면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenSortIsLatest() {
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange("/api/v1/products?sort=LATEST", HttpMethod.GET, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).hasSize(1);
        }

        @DisplayName("sort=latest처럼 소문자로 주면, enum 값과 대소문자가 일치하지 않아 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenSortIsLowerCase() {
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange("/api/v1/products?sort=latest", HttpMethod.GET, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL);
        }

        @DisplayName("sort=PRICE_ASC를 주면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenSortIsPriceAsc() {
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange("/api/v1/products?sort=PRICE_ASC", HttpMethod.GET, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).hasSize(1);
        }

        @DisplayName("sort=INVALID처럼 존재하지 않는 값을 주면, 400 BAD_REQUEST와 표준 에러 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenSortIsInvalid() {
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                    testRestTemplate.exchange("/api/v1/products?sort=INVALID", HttpMethod.GET, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL);
            assertThat(response.getBody().meta().errorCode()).isNotBlank();
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
            assertThat(response.getBody().data().likeCount()).isEqualTo(0L);
        }

        @DisplayName("찜이 존재하면, likeCount가 정확히 반환된다.")
        @Test
        void returnsCorrectLikeCount_whenWishlistExists() {
            wishlistRepository.save(new WishlistModel(1L, product.getId()));
            wishlistRepository.save(new WishlistModel(2L, product.getId()));
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange("/api/v1/products/" + product.getId(), HttpMethod.GET, null, type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().likeCount()).isEqualTo(2L);
        }
    }
}
