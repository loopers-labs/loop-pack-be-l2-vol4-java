package com.loopers.product.interfaces.api;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.like.domain.LikeService;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.stock.domain.ProductStockService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";
    private static final String ENDPOINT_PRODUCT_DETAIL = "/api/v1/products/{productId}";

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final LikeService likeService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandService brandService,
        ProductService productService,
        ProductStockService productStockService,
        LikeService likeService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandService = brandService;
        this.productService = productService;
        this.productStockService = productStockService;
        this.likeService = likeService;
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

        @DisplayName("삭제된 상품 ID가 주어지면 404 NOT FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductIsDeleted() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product product = createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            productService.deleteProduct(product.getId());

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = getProduct(product.getId());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드의 상품 ID가 주어지면 404 NOT FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenBrandIsDeleted() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product product = createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            brandService.deleteBrand(brand.getId());

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = getProduct(product.getId());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("미삭제 상품들이 주어지면 200 OK와 브랜드 정보, 좋아요 수를 포함한 최신순 상품 목록을 반환한다.")
        @Test
        void returnsProductsByLatest_whenProductsExist() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            createProduct(brand, "아이폰 16", 1_250_000L, 7);
            Product pro = createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            Product proMax = createProduct(brand, "아이폰 16 Pro Max", 1_900_000L, 5);
            likeProduct(pro, 101L);
            likeProduct(proMax, 101L, 102L);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = getProducts();

            // assert
            PageResponse<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.content())
                    .extracting(ProductV1Dto.ProductResponse::name)
                    .containsExactly("아이폰 16 Pro Max", "아이폰 16 Pro", "아이폰 16"),
                () -> assertThat(data.content())
                    .extracting(ProductV1Dto.ProductResponse::likeCount)
                    .containsExactly(2L, 1L, 0L),
                () -> assertThat(data.content())
                    .extracting(product -> product.brand().name())
                    .containsOnly("애플"),
                () -> assertThat(data.totalElements()).isEqualTo(3),
                () -> assertThat(data.totalPages()).isEqualTo(1),
                () -> assertThat(data.number()).isZero(),
                () -> assertThat(data.size()).isEqualTo(20),
                () -> assertThat(data.first()).isTrue(),
                () -> assertThat(data.last()).isTrue()
            );
        }

        @DisplayName("sort=price_asc가 주어지면 200 OK와 가격 오름차순 상품 목록을 반환한다.")
        @Test
        void returnsProductsByPriceAsc_whenSortIsPriceAsc() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            createProduct(brand, "아이폰 16", 1_250_000L, 7);
            createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            createProduct(brand, "아이폰 16 Pro Max", 1_900_000L, 5);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = getProducts("?sort=price_asc");

            // assert
            PageResponse<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.content())
                    .extracting(ProductV1Dto.ProductResponse::name)
                    .containsExactly("아이폰 16", "아이폰 16 Pro", "아이폰 16 Pro Max")
            );
        }

        @DisplayName("sort=likes_desc가 주어지면 200 OK와 좋아요 수 내림차순 상품 목록을 반환한다.")
        @Test
        void returnsProductsByLikesDesc_whenSortIsLikesDesc() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = createProduct(brand, "아이폰 16", 1_250_000L, 7);
            Product pro = createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            Product proMax = createProduct(brand, "아이폰 16 Pro Max", 1_900_000L, 5);
            likeProduct(iphone, 101L, 102L);
            likeProduct(proMax, 101L);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = getProducts("?sort=likes_desc");

            // assert
            PageResponse<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.content())
                    .extracting(ProductV1Dto.ProductResponse::name)
                    .containsExactly("아이폰 16", "아이폰 16 Pro Max", "아이폰 16 Pro"),
                () -> assertThat(data.content())
                    .extracting(ProductV1Dto.ProductResponse::likeCount)
                    .containsExactly(2L, 1L, 0L)
            );
        }

        @DisplayName("삭제된 상품이 있으면 200 OK와 삭제 상품을 제외한 목록을 반환한다.")
        @Test
        void excludesDeletedProduct_whenProductIsDeleted() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            createProduct(brand, "아이폰 16", 1_250_000L, 7);
            Product deletedProduct = createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            productService.deleteProduct(deletedProduct.getId());

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = getProducts();

            // assert
            PageResponse<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.content())
                    .extracting(ProductV1Dto.ProductResponse::name)
                    .containsExactly("아이폰 16"),
                () -> assertThat(data.totalElements()).isEqualTo(1)
            );
        }

        @DisplayName("삭제된 브랜드 ID로 필터링하면 200 OK와 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenBrandFilterIsDeleted() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            brandService.deleteBrand(brand.getId());

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = getProducts("?brandId=" + brand.getId());

            // assert
            PageResponse<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.content()).isEmpty(),
                () -> assertThat(data.totalElements()).isZero()
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID로 필터링하면 200 OK와 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenBrandFilterDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = getProducts("?brandId=999999");

            // assert
            PageResponse<ProductV1Dto.ProductResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.content()).isEmpty(),
                () -> assertThat(data.totalElements()).isZero()
            );
        }
    }

    private Product createProduct(Brand brand, String name, long price, int stockQuantity) {
        Product product = productService.createProduct(
            brand.getId(),
            name,
            "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
            price
        );
        productStockService.createProductStock(product.getId(), stockQuantity);
        return product;
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> getProducts() {
        return getProducts("");
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> getProducts(String query) {
        ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + query,
            HttpMethod.GET,
            null,
            responseType
        );
    }

    private void likeProduct(Product product, Long... userIds) {
        for (Long userId : userIds) {
            likeService.like(userId, product.getId());
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
