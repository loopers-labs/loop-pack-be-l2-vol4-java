package com.loopers.product.interfaces.api;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.config.redis.RedisConfig;
import com.loopers.like.application.LikeFacade;
import com.loopers.product.application.ProductLikeSummarySynchronizer;
import com.loopers.product.application.ProductLikeSummaryWriter;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.product.infrastructure.ProductDetailCacheProperties;
import com.loopers.stock.domain.ProductStockService;
import com.loopers.shared.presentation.ApiResponse;
import com.loopers.shared.presentation.PageResponse;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.springframework.beans.factory.annotation.Qualifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainersConfig.class)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";
    private static final String ENDPOINT_PRODUCT_DETAIL = "/api/v1/products/{productId}";
    private static final String PRODUCT_DETAIL_CACHE_KEY_PREFIX = "product:detail:v1:";
    private static final String PRODUCT_LIST_CACHE_KEY_PREFIX = "product:list:v1:";

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final LikeFacade likeFacade;
    private final ProductLikeSummaryWriter productLikeSummaryWriter;
    private final ProductLikeSummarySynchronizer productLikeSummarySynchronizer;
    private final ProductDetailCacheProperties productDetailCacheProperties;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;
    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    ProductV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandService brandService,
        ProductService productService,
        ProductStockService productStockService,
        LikeFacade likeFacade,
        ProductLikeSummaryWriter productLikeSummaryWriter,
        ProductLikeSummarySynchronizer productLikeSummarySynchronizer,
        ProductDetailCacheProperties productDetailCacheProperties,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp,
        JdbcTemplate jdbcTemplate,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandService = brandService;
        this.productService = productService;
        this.productStockService = productStockService;
        this.likeFacade = likeFacade;
        this.productLikeSummaryWriter = productLikeSummaryWriter;
        this.productLikeSummarySynchronizer = productLikeSummarySynchronizer;
        this.productDetailCacheProperties = productDetailCacheProperties;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
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

        @DisplayName("상품 상세 조회는 요약 테이블의 좋아요 수를 반환한다.")
        @Test
        void returnsProductDetailLikeCountFromSummary() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product product = createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            changeSummaryLikeCount(product.getId(), 7);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = getProduct(product.getId());

            // assert
            ProductV1Dto.ProductResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.id()).isEqualTo(product.getId()),
                () -> assertThat(data.likeCount()).isEqualTo(7L)
            );
        }

        @DisplayName("상품 상세 조회 결과가 캐시되면 TTL 안에서는 캐시된 좋아요 수를 반환한다.")
        @Test
        void returnsCachedProductDetail_whenSummaryChangesBeforeCacheExpires() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product product = createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            changeSummaryLikeCount(product.getId(), 7);
            getProduct(product.getId());
            changeSummaryLikeCount(product.getId(), 11);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = getProduct(product.getId());

            // assert
            ProductV1Dto.ProductResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.id()).isEqualTo(product.getId()),
                () -> assertThat(data.likeCount()).isEqualTo(7L)
            );
        }

        @DisplayName("상품 상세 조회 결과를 Redis에 TTL과 함께 저장한다.")
        @Test
        void storesProductDetailCacheWithTtl_whenProductDetailIsReturned() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 편리하게 만드는 브랜드");
            Product product = createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            changeSummaryLikeCount(product.getId(), 7);

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = getProduct(product.getId());

            // assert
            String cacheKey = productDetailCacheKey(product.getId());
            Long ttlSeconds = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(redisTemplate.hasKey(cacheKey)).isTrue(),
                () -> assertThat(redisTemplate.opsForValue().get(cacheKey)).contains("\"likeCount\":7"),
                () -> assertThat(ttlSeconds).isBetween(
                    1L,
                    productDetailCacheProperties.ttlSeconds() + productDetailCacheProperties.jitterSeconds()
                )
            );
        }

        @DisplayName("존재하지 않는 상품 상세 조회 결과를 Redis에 짧은 TTL로 저장한다.")
        @Test
        void storesNegativeCacheWithShortTtl_whenProductDoesNotExist() {
            // arrange
            Long missingProductId = 999_999L;

            // act
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = getProduct(missingProductId);

            // assert
            String cacheKey = productDetailCacheKey(missingProductId);
            Long ttlSeconds = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(redisTemplate.hasKey(cacheKey)).isTrue(),
                () -> assertThat(ttlSeconds).isBetween(1L, productDetailCacheProperties.negativeTtlSeconds())
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

        @DisplayName("상품 목록 조회 결과를 Redis에 TTL과 함께 저장한다.")
        @Test
        void storesProductListCacheWithTtl_whenProductListIsReturned() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 편리하게 만드는 브랜드");
            Product product = createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            changeSummaryLikeCount(product.getId(), 7);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                getProducts("?brandId=" + brand.getId() + "&sort=likes_desc&page=0&size=20");

            // assert
            String cacheKey = productListCacheKey(brand.getId(), "likes_desc", 0, 20);
            Long ttlSeconds = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(redisTemplate.hasKey(cacheKey)).isTrue(),
                () -> assertThat(redisTemplate.opsForValue().get(cacheKey)).contains("\"likeCount\":7"),
                () -> assertThat(ttlSeconds).isBetween(1L, 13L)
            );
        }

        @DisplayName("상품 목록 조회 결과가 캐시되면 TTL 안에서는 캐시된 좋아요순 목록을 반환한다.")
        @Test
        void returnsCachedProductList_whenSummaryChangesBeforeCacheExpires() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 편리하게 만드는 브랜드");
            Product iphone = createProduct(brand, "아이폰 16", 1_250_000L, 7);
            Product pro = createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            Product proMax = createProduct(brand, "아이폰 16 Pro Max", 1_900_000L, 5);
            changeSummaryLikeCount(iphone.getId(), 2);
            changeSummaryLikeCount(pro.getId(), 0);
            changeSummaryLikeCount(proMax.getId(), 1);
            String query = "?brandId=" + brand.getId() + "&sort=likes_desc&page=0&size=20";
            getProducts(query);
            changeSummaryLikeCount(iphone.getId(), 0);
            changeSummaryLikeCount(pro.getId(), 9);
            changeSummaryLikeCount(proMax.getId(), 1);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response = getProducts(query);

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

        @DisplayName("캐시 대상 크기를 넘는 상품 목록 조회 결과는 Redis에 저장하지 않는다.")
        @Test
        void doesNotStoreProductListCache_whenPageSizeIsTooLarge() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 편리하게 만드는 브랜드");
            createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                getProducts("?brandId=" + brand.getId() + "&sort=latest&page=0&size=51");

            // assert
            String cacheKey = productListCacheKey(brand.getId(), "latest", 0, 51);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(redisTemplate.hasKey(cacheKey)).isFalse()
            );
        }

        @DisplayName("세 번째 페이지 이후라도 상위 노출 구간이면 상품 목록 조회 결과를 Redis에 저장한다.")
        @Test
        void storesProductListCache_whenPageIsWithinTopItems() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 편리하게 만드는 브랜드");
            createProduct(brand, "아이폰 16", 1_250_000L, 7);
            createProduct(brand, "아이폰 16 Plus", 1_350_000L, 7);
            createProduct(brand, "아이폰 16 Pro", 1_550_000L, 10);
            createProduct(brand, "아이폰 16 Pro Max", 1_900_000L, 5);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductResponse>>> response =
                getProducts("?brandId=" + brand.getId() + "&sort=latest&page=3&size=1");

            // assert
            String cacheKey = productListCacheKey(brand.getId(), "latest", 3, 1);
            Long ttlSeconds = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(redisTemplate.hasKey(cacheKey)).isTrue(),
                () -> assertThat(ttlSeconds).isBetween(1L, 13L)
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
        productLikeSummaryWriter.initialize(product.getId(), product.getBrandId());
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
            likeFacade.like(userId, product.getId());
        }
        productLikeSummarySynchronizer.sync();
    }

    private void changeSummaryLikeCount(Long productId, long likeCount) {
        jdbcTemplate.update(
            "UPDATE product_like_summary SET like_count = ? WHERE product_id = ?",
            likeCount,
            productId
        );
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

    private String productDetailCacheKey(Long productId) {
        return PRODUCT_DETAIL_CACHE_KEY_PREFIX + productId;
    }

    private String productListCacheKey(Long brandId, String sort, int page, int size) {
        String brandKey = brandId == null ? "all" : String.valueOf(brandId);
        return PRODUCT_LIST_CACHE_KEY_PREFIX
            + "brand:" + brandKey
            + ":sort:" + sort
            + ":page:" + page
            + ":size:" + size;
    }
}
