package com.loopers.interfaces.api.catalog;

import com.loopers.CommerceApiApplication;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductStatus;
import com.loopers.interfaces.api.catalog.brand.BrandAdminDto;
import com.loopers.interfaces.api.catalog.brand.BrandV1Dto;
import com.loopers.interfaces.api.catalog.like.ProductLikeDto;
import com.loopers.interfaces.api.catalog.product.ProductAdminDto;
import com.loopers.interfaces.api.catalog.product.ProductV1Dto;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = CommerceApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    CatalogApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandRepository brandRepository,
        ProductRepository productRepository,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp,
        @Qualifier("redisTemplateMaster")
        RedisTemplate<String, String> redisTemplate
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.redisTemplate = redisTemplate;
    }

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @DisplayName("활성 브랜드만 공개 조회한다.")
        @Test
        void returnsOnlyActiveBrand() {
            // arrange
            Brand activeBrand = saveBrand("Loopers", "테스트 브랜드");
            Brand deletedBrand = saveBrand("Deleted", "삭제 브랜드");
            deletedBrand.delete();
            brandRepository.save(deletedBrand);

            // act
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> activeResponse = testRestTemplate.exchange(
                "/api/v1/brands/" + activeBrand.getId(),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                responseType
            );
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> deletedResponse = testRestTemplate.exchange(
                "/api/v1/brands/" + deletedBrand.getId(),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                responseType
            );

            // assert
            assertAll(
                () -> assertTrue(activeResponse.getStatusCode().is2xxSuccessful()),
                () -> assertThat(activeResponse.getBody().data().name()).isEqualTo("Loopers"),
                () -> assertThat(deletedResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("brandId로 필터링하고 판매 중인 상품만 페이지 응답으로 조회한다.")
        @Test
        void returnsOnlyOnSaleProductsByBrand() {
            // arrange
            Brand brand = saveBrand("Loopers", "테스트 브랜드");
            Brand otherBrand = saveBrand("Other", "다른 브랜드");
            Product onSaleProduct = saveProduct(brand, "상품1", 1_000L, 10);
            Product stoppedProduct = saveProduct(brand, "상품2", 2_000L, 10);
            stoppedProduct.stopSelling();
            productRepository.save(stoppedProduct);
            saveProduct(otherBrand, "다른상품", 500L, 10);

            // act
            ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductListItemResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductListItemResponse>>> response =
                testRestTemplate.exchange(
                    "/api/v1/products?brandId=" + brand.getId() + "&page=0&size=20&sort=latest",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().pageInfo().totalElements()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).productId()).isEqualTo(onSaleProduct.getId()),
                () -> assertThat(response.getBody().data().items().get(0).status()).isEqualTo(ProductStatus.ON_SALE)
            );
        }

        @DisplayName("좋아요 순 정렬 결과를 반환하고 상품 목록 캐시를 생성한다.")
        @Test
        void returnsProductsSortedByLikesDescAndCreatesListCache() {
            // arrange
            Brand brand = saveBrand("Loopers", "테스트 브랜드");
            Product lowLikedProduct = saveProductWithLikeCount(brand, "좋아요 1개", 1_000L, 10, 1);
            Product highLikedProduct = saveProductWithLikeCount(brand, "좋아요 3개", 2_000L, 10, 3);
            Product middleLikedProduct = saveProductWithLikeCount(brand, "좋아요 2개", 3_000L, 10, 2);

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductListItemResponse>>> response =
                getProducts("/api/v1/products?brandId=" + brand.getId() + "&page=0&size=20&sort=likes_desc");

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().items())
                    .extracting(ProductV1Dto.ProductListItemResponse::productId)
                    .containsExactly(highLikedProduct.getId(), middleLikedProduct.getId(), lowLikedProduct.getId()),
                () -> assertThat(response.getBody().data().items())
                    .extracting(ProductV1Dto.ProductListItemResponse::likeCount)
                    .containsExactly(3L, 2L, 1L),
                () -> assertThat(listCacheKeys()).isNotEmpty()
            );
        }

        @DisplayName("좋아요 변경 후 상품 목록 캐시를 무효화하고 최신 좋아요 순 정렬을 반환한다.")
        @Test
        void evictsListCache_whenLikeCountChanges() {
            // arrange
            Brand brand = saveBrand("Loopers", "테스트 브랜드");
            Product firstProduct = saveProductWithLikeCount(brand, "기존 인기 상품", 1_000L, 10, 1);
            Product secondProduct = saveProduct(brand, "새 인기 상품", 2_000L, 10);
            String url = "/api/v1/products?brandId=" + brand.getId() + "&page=0&size=20&sort=likes_desc";
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductListItemResponse>>> cachedResponse = getProducts(url);
            assertThat(cachedResponse.getBody().data().items().get(0).productId()).isEqualTo(firstProduct.getId());
            assertThat(listCacheKeys()).isNotEmpty();

            // act
            likeProduct("user1", secondProduct.getId());
            likeProduct("user2", secondProduct.getId());
            ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductListItemResponse>>> response = getProducts(url);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().items().get(0).productId()).isEqualTo(secondProduct.getId()),
                () -> assertThat(response.getBody().data().items().get(0).likeCount()).isEqualTo(2L),
                () -> assertThat(response.getBody().data().items().get(1).productId()).isEqualTo(firstProduct.getId())
            );
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("상품 상세에 재고와 브랜드 요약을 함께 반환한다.")
        @Test
        void returnsProductDetailWithStockQuantityAndBrandSummary() {
            // arrange
            Brand brand = saveBrand("Loopers", "테스트 브랜드");
            Product product = saveProduct(brand, "상품", 1_000L, 10);

            // act
            ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                responseType
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().productId()).isEqualTo(product.getId()),
                () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(10),
                () -> assertThat(response.getBody().data().brand().brandId()).isEqualTo(brand.getId()),
                () -> assertThat(response.getBody().data().brand().name()).isEqualTo("Loopers")
            );
        }

        @DisplayName("좋아요 변경 후 상품 상세 캐시를 무효화하고 최신 좋아요 수와 사용자 좋아요 여부를 반환한다.")
        @Test
        void evictsDetailCache_whenLikeCountChanges() {
            // arrange
            Product product = saveProduct("상품", 1_000L, 10);
            ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> cachedResponse =
                getProduct(product.getId(), null);
            assertThat(cachedResponse.getBody().data().likeCount()).isZero();

            // act
            ResponseEntity<ApiResponse<ProductLikeDto.ProductLikeResponse>> likeResponse = likeProduct("user1", product.getId());
            ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response = getProduct(product.getId(), "user1");

            // assert
            assertAll(
                () -> assertTrue(likeResponse.getStatusCode().is2xxSuccessful()),
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().productId()).isEqualTo(product.getId()),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().liked()).isTrue()
            );
        }
    }

    @DisplayName("좋아요 API")
    @Nested
    class ProductLikeApi {

        @DisplayName("좋아요 등록, 중복 등록, 내 목록 조회, 취소를 처리한다.")
        @Test
        void managesProductLikesIdempotently() {
            // arrange
            Product product = saveProduct("상품", 1_000L, 10);

            // act
            ResponseEntity<ApiResponse<ProductLikeDto.ProductLikeResponse>> likeResponse = likeProduct("user1", product.getId());
            ResponseEntity<ApiResponse<ProductLikeDto.ProductLikeResponse>> duplicatedLikeResponse = likeProduct("user1", product.getId());
            ResponseEntity<ApiResponse<PageResponse<ProductLikeDto.ProductLikeResponse>>> myLikesResponse =
                getMyLikes("user1", "user1");
            ResponseEntity<ApiResponse<PageResponse<ProductLikeDto.ProductLikeResponse>>> otherUsersLikesResponse =
                getMyLikes("user1", "user2");
            ResponseEntity<ApiResponse<ProductLikeDto.ProductLikeResponse>> unlikeResponse = unlikeProduct("user1", product.getId());

            // assert
            assertAll(
                () -> assertTrue(likeResponse.getStatusCode().is2xxSuccessful()),
                () -> assertThat(likeResponse.getBody().data().liked()).isTrue(),
                () -> assertThat(likeResponse.getBody().data().likeCount()).isEqualTo(1L),
                () -> assertThat(duplicatedLikeResponse.getBody().data().likeCount()).isEqualTo(1L),
                () -> assertThat(myLikesResponse.getBody().data().pageInfo().totalElements()).isEqualTo(1L),
                () -> assertThat(myLikesResponse.getBody().data().items().get(0).productId()).isEqualTo(product.getId()),
                () -> assertThat(otherUsersLikesResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertTrue(unlikeResponse.getStatusCode().is2xxSuccessful()),
                () -> assertThat(unlikeResponse.getBody().data().liked()).isFalse(),
                () -> assertThat(unlikeResponse.getBody().data().likeCount()).isZero()
            );
        }
    }

    @DisplayName("브랜드 ADMIN API")
    @Nested
    class BrandAdminApi {

        @DisplayName("브랜드 등록, 수정, 목록, 상세, 삭제를 처리한다.")
        @Test
        void managesBrands() {
            // act
            ResponseEntity<ApiResponse<BrandAdminDto.BrandResponse>> createResponse = createBrand(
                new BrandAdminDto.CreateBrandRequest("Loopers", "테스트 브랜드")
            );
            Long brandId = createResponse.getBody().data().id();
            ResponseEntity<ApiResponse<BrandAdminDto.BrandResponse>> updateResponse = updateBrand(
                brandId,
                new BrandAdminDto.UpdateBrandRequest("Loopers Updated", "수정 브랜드")
            );
            ResponseEntity<ApiResponse<PageResponse<BrandAdminDto.BrandResponse>>> listResponse = getAdminBrands();
            ResponseEntity<ApiResponse<BrandAdminDto.BrandResponse>> detailResponse = getAdminBrand(brandId);
            ResponseEntity<ApiResponse<Void>> deleteResponse = deleteBrand(brandId);
            ResponseEntity<ApiResponse<BrandAdminDto.BrandResponse>> deletedDetailResponse = getAdminBrand(brandId);

            // assert
            assertAll(
                () -> assertTrue(createResponse.getStatusCode().is2xxSuccessful()),
                () -> assertThat(updateResponse.getBody().data().name()).isEqualTo("Loopers Updated"),
                () -> assertThat(listResponse.getBody().data().pageInfo().totalElements()).isEqualTo(1L),
                () -> assertThat(detailResponse.getBody().data().active()).isTrue(),
                () -> assertTrue(deleteResponse.getStatusCode().is2xxSuccessful()),
                () -> assertThat(deletedDetailResponse.getBody().data().active()).isFalse()
            );
        }

        @DisplayName("어드민 헤더가 올바르지 않으면 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenAdminHeaderIsInvalid() {
            // act
            ParameterizedTypeReference<ApiResponse<PageResponse<BrandAdminDto.BrandResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<BrandAdminDto.BrandResponse>>> response =
                testRestTemplate.exchange(
                    "/api-admin/v1/brands?page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders("invalid.admin")),
                    responseType
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("상품 ADMIN API")
    @Nested
    class ProductAdminApi {

        @DisplayName("상품 등록, 수정, 목록, 상세, 판매 중지를 처리한다.")
        @Test
        void managesProducts() {
            // arrange
            Brand brand = saveBrand("Loopers", "테스트 브랜드");

            // act
            ResponseEntity<ApiResponse<ProductAdminDto.ProductResponse>> createResponse = createProduct(
                new ProductAdminDto.CreateProductRequest(brand.getId(), "상품", "설명", 1_000L, 10)
            );
            Long productId = createResponse.getBody().data().id();
            ResponseEntity<ApiResponse<ProductAdminDto.ProductResponse>> updateResponse = updateProduct(
                productId,
                new ProductAdminDto.UpdateProductRequest("수정상품", "수정 설명", 2_000L, 5)
            );
            ResponseEntity<ApiResponse<PageResponse<ProductAdminDto.ProductResponse>>> listResponse =
                getAdminProducts(brand.getId());
            ResponseEntity<ApiResponse<ProductAdminDto.ProductResponse>> detailResponse = getAdminProduct(productId);
            ResponseEntity<ApiResponse<Void>> deleteResponse = deleteProduct(productId);
            ResponseEntity<ApiResponse<ProductAdminDto.ProductResponse>> stoppedDetailResponse = getAdminProduct(productId);

            // assert
            assertAll(
                () -> assertTrue(createResponse.getStatusCode().is2xxSuccessful()),
                () -> assertThat(updateResponse.getBody().data().name()).isEqualTo("수정상품"),
                () -> assertThat(updateResponse.getBody().data().price()).isEqualTo(2_000L),
                () -> assertThat(listResponse.getBody().data().pageInfo().totalElements()).isEqualTo(1L),
                () -> assertThat(detailResponse.getBody().data().stockQuantity()).isEqualTo(5),
                () -> assertTrue(deleteResponse.getStatusCode().is2xxSuccessful()),
                () -> assertThat(stoppedDetailResponse.getBody().data().status()).isEqualTo(ProductStatus.STOPPED)
            );
        }

        @DisplayName("상품 등록 시 요청 status를 반영한다.")
        @Test
        void createsProductWithRequestedStatus() {
            // arrange
            Brand brand = saveBrand("Loopers", "테스트 브랜드");

            // act
            ResponseEntity<ApiResponse<ProductAdminDto.ProductResponse>> response = createProduct(
                new ProductAdminDto.CreateProductRequest(
                    brand.getId(),
                    "품절 상품",
                    "설명",
                    1_000L,
                    10,
                    ProductStatus.SOLD_OUT
                )
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(ProductStatus.SOLD_OUT)
            );
        }
    }

    private Brand saveBrand(String name, String description) {
        return brandRepository.save(new Brand(name, description));
    }

    private Product saveProduct(Brand brand, String name, Long price, Integer stockQuantity) {
        return productRepository.save(new Product(brand.getId(), name, "설명", price, stockQuantity));
    }

    private Product saveProduct(String name, Long price, Integer stockQuantity) {
        Brand brand = saveBrand("Loopers", "테스트 브랜드");
        return saveProduct(brand, name, price, stockQuantity);
    }

    private Product saveProductWithLikeCount(
        Brand brand,
        String name,
        Long price,
        Integer stockQuantity,
        int likeCount
    ) {
        Product product = new Product(brand.getId(), name, "설명", price, stockQuantity);
        for (int i = 0; i < likeCount; i++) {
            product.increaseLikeCount();
        }
        return productRepository.save(product);
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductV1Dto.ProductListItemResponse>>> getProducts(String url) {
        ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductListItemResponse>>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, responseType);
    }

    private ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> getProduct(Long productId, String userId) {
        ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        HttpEntity<?> entity = userId == null ? HttpEntity.EMPTY : new HttpEntity<>(userHeaders(userId));
        return testRestTemplate.exchange(
            "/api/v1/products/" + productId,
            HttpMethod.GET,
            entity,
            responseType
        );
    }

    private Set<String> listCacheKeys() {
        return redisTemplate.opsForSet().members("commerce:product:list:v1:keys");
    }

    private ResponseEntity<ApiResponse<BrandAdminDto.BrandResponse>> createBrand(
        BrandAdminDto.CreateBrandRequest request
    ) {
        ParameterizedTypeReference<ApiResponse<BrandAdminDto.BrandResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api-admin/v1/brands",
            HttpMethod.POST,
            new HttpEntity<>(request, adminHeaders("loopers.admin")),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<BrandAdminDto.BrandResponse>> updateBrand(
        Long brandId,
        BrandAdminDto.UpdateBrandRequest request
    ) {
        ParameterizedTypeReference<ApiResponse<BrandAdminDto.BrandResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api-admin/v1/brands/" + brandId,
            HttpMethod.PUT,
            new HttpEntity<>(request, adminHeaders("loopers.admin")),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<BrandAdminDto.BrandResponse>>> getAdminBrands() {
        ParameterizedTypeReference<ApiResponse<PageResponse<BrandAdminDto.BrandResponse>>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api-admin/v1/brands?page=0&size=20",
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders("loopers.admin")),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<BrandAdminDto.BrandResponse>> getAdminBrand(Long brandId) {
        ParameterizedTypeReference<ApiResponse<BrandAdminDto.BrandResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api-admin/v1/brands/" + brandId,
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders("loopers.admin")),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<Void>> deleteBrand(Long brandId) {
        ParameterizedTypeReference<ApiResponse<Void>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api-admin/v1/brands/" + brandId,
            HttpMethod.DELETE,
            new HttpEntity<>(adminHeaders("loopers.admin")),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<ProductAdminDto.ProductResponse>> createProduct(
        ProductAdminDto.CreateProductRequest request
    ) {
        ParameterizedTypeReference<ApiResponse<ProductAdminDto.ProductResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api-admin/v1/products",
            HttpMethod.POST,
            new HttpEntity<>(request, adminHeaders("loopers.admin")),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<ProductAdminDto.ProductResponse>> updateProduct(
        Long productId,
        ProductAdminDto.UpdateProductRequest request
    ) {
        ParameterizedTypeReference<ApiResponse<ProductAdminDto.ProductResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api-admin/v1/products/" + productId,
            HttpMethod.PUT,
            new HttpEntity<>(request, adminHeaders("loopers.admin")),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductAdminDto.ProductResponse>>> getAdminProducts(Long brandId) {
        ParameterizedTypeReference<ApiResponse<PageResponse<ProductAdminDto.ProductResponse>>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api-admin/v1/products?brandId=" + brandId + "&page=0&size=20",
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders("loopers.admin")),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<ProductAdminDto.ProductResponse>> getAdminProduct(Long productId) {
        ParameterizedTypeReference<ApiResponse<ProductAdminDto.ProductResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api-admin/v1/products/" + productId,
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders("loopers.admin")),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<Void>> deleteProduct(Long productId) {
        ParameterizedTypeReference<ApiResponse<Void>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api-admin/v1/products/" + productId,
            HttpMethod.DELETE,
            new HttpEntity<>(adminHeaders("loopers.admin")),
            responseType
        );
    }

    private HttpHeaders adminHeaders(String ldap) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HeaderValidator.ADMIN_LDAP, ldap);
        return headers;
    }

    private ResponseEntity<ApiResponse<ProductLikeDto.ProductLikeResponse>> likeProduct(String userId, Long productId) {
        ParameterizedTypeReference<ApiResponse<ProductLikeDto.ProductLikeResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api/v1/products/" + productId + "/likes",
            HttpMethod.POST,
            new HttpEntity<>(userHeaders(userId)),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<ProductLikeDto.ProductLikeResponse>> unlikeProduct(String userId, Long productId) {
        ParameterizedTypeReference<ApiResponse<ProductLikeDto.ProductLikeResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api/v1/products/" + productId + "/likes",
            HttpMethod.DELETE,
            new HttpEntity<>(userHeaders(userId)),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductLikeDto.ProductLikeResponse>>> getMyLikes(
        String loginId,
        String pathUserId
    ) {
        ParameterizedTypeReference<ApiResponse<PageResponse<ProductLikeDto.ProductLikeResponse>>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api/v1/users/" + pathUserId + "/likes?page=0&size=20",
            HttpMethod.GET,
            new HttpEntity<>(userHeaders(loginId)),
            responseType
        );
    }

    private HttpHeaders userHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HeaderValidator.LOGIN_ID, userId);
        headers.add(HeaderValidator.LOGIN_PW, "password");
        return headers;
    }
}
