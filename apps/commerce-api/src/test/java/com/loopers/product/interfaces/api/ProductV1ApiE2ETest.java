package com.loopers.product.interfaces.api;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductDisplayStatus;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    private Long brandId;

    @Autowired
    public ProductV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandRepository brandRepository,
            ProductRepository productRepository,
            ProductStockRepository productStockRepository,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.productStockRepository = productStockRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        brandId = brandRepository.save(Brand.create("나이키", "설명", "https://cdn/brand.png")).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Product saveOnSale(String name, long price, int stock) {
        Product product = productRepository.save(Product.create(brandId, name, "설명", price, "https://cdn/" + name + ".png"));
        productStockRepository.save(ProductStock.create(product.getId(), stock));
        return product;
    }

    private Product saveSuspended(String name, long price, int stock) {
        Product product = Product.create(brandId, name, "설명", price, null);
        product.suspend();
        product = productRepository.save(product);
        productStockRepository.save(ProductStock.create(product.getId(), stock));
        return product;
    }

    private ResponseEntity<ApiResponse<ProductV1Response.Detail>> getById(Long productId) {
        ParameterizedTypeReference<ApiResponse<ProductV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT + "/" + productId, HttpMethod.GET, null, type);
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetOne {

        @Test
        @DisplayName("판매중 상품을 조회하면 200 과 브랜드명·좋아요수·displayStatus=ON_SALE 을 반환한다")
        void givenOnSaleProduct_whenGet_thenReturnsDetail() {
            Product product = saveOnSale("셔츠", 29_000L, 10);

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = getById(product.getId());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("셔츠"),
                    () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0L),
                    () -> assertThat(response.getBody().data().displayStatus()).isEqualTo(ProductDisplayStatus.ON_SALE)
            );
        }

        @Test
        @DisplayName("재고가 0 인 판매중 상품은 displayStatus=SOLD_OUT 으로 반환한다")
        void givenZeroStock_whenGet_thenSoldOut() {
            Product product = saveOnSale("품절셔츠", 29_000L, 0);

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = getById(product.getId());

            assertThat(response.getBody().data().displayStatus()).isEqualTo(ProductDisplayStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("판매중지 상품은 사용자 조회에서 404 NOT_FOUND 이다")
        void givenSuspendedProduct_whenGet_thenNotFound() {
            Product product = saveSuspended("판매중지", 29_000L, 10);

            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = getById(product.getId());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 productId 는 404 NOT_FOUND 이다")
        void givenNonExisting_whenGet_thenNotFound() {
            ResponseEntity<ApiResponse<ProductV1Response.Detail>> response = getById(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetAll {

        private ResponseEntity<ApiResponse<ProductV1Response.Page>> getAll(String query) {
            ParameterizedTypeReference<ApiResponse<ProductV1Response.Page>> type = new ParameterizedTypeReference<>() {};
            return testRestTemplate.exchange(ENDPOINT + query, HttpMethod.GET, null, type);
        }

        @Test
        @DisplayName("판매중 상품만 content 로 반환하고 판매중지 상품은 제외한다")
        void givenMixedProducts_whenGetAll_thenReturnsOnlyOnSale() {
            saveOnSale("A", 1000L, 5);
            saveOnSale("B", 2000L, 5);
            saveSuspended("판매중지", 3000L, 5);

            ResponseEntity<ApiResponse<ProductV1Response.Page>> response = getAll("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductV1Response.Detail::name)
                            .containsExactlyInAnyOrder("A", "B"),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductV1Response.Detail::brandName)
                            .containsOnly("나이키")
            );
        }

        @Test
        @DisplayName("sort=PRICE_ASC 로 가격 오름차순 정렬해 content 를 반환한다")
        void givenPriceAsc_whenGetAll_thenOrdered() {
            saveOnSale("비싼것", 50_000L, 5);
            saveOnSale("싼것", 10_000L, 5);

            ResponseEntity<ApiResponse<ProductV1Response.Page>> response = getAll("?sort=PRICE_ASC");

            assertThat(response.getBody().data().content())
                    .extracting(ProductV1Response.Detail::name)
                    .containsExactly("싼것", "비싼것");
        }

        @Test
        @DisplayName("totalCount 는 판매중 상품 전체 수를 반환한다(페이지 크기와 무관)")
        void givenManyProducts_whenGetAll_thenTotalCountReflectsAll() {
            saveOnSale("A", 1000L, 5);
            saveOnSale("B", 2000L, 5);
            saveOnSale("C", 3000L, 5);

            ResponseEntity<ApiResponse<ProductV1Response.Page>> response = getAll("?size=2");

            assertAll(
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().totalCount()).isEqualTo(3L),
                    () -> assertThat(response.getBody().data().size()).isEqualTo(2)
            );
        }

        @Test
        @DisplayName("page/size 로 해당 페이지 슬라이스만 반환한다")
        void givenPageAndSize_whenGetAll_thenReturnsSlice() {
            saveOnSale("p1", 1000L, 5);
            saveOnSale("p2", 2000L, 5);
            saveOnSale("p3", 3000L, 5);
            saveOnSale("p4", 4000L, 5);
            saveOnSale("p5", 5000L, 5);

            ResponseEntity<ApiResponse<ProductV1Response.Page>> response = getAll("?sort=PRICE_ASC&page=1&size=2");

            assertAll(
                    () -> assertThat(response.getBody().data().page()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductV1Response.Detail::name)
                            .containsExactly("p3", "p4")
            );
        }

        @Test
        @DisplayName("brandId 필터를 주면 해당 브랜드의 상품만 반환한다")
        void givenBrandFilter_whenGetAll_thenReturnsOnlyThatBrand() {
            Long otherBrandId = brandRepository.save(Brand.create("아디다스", "설명", "https://cdn/adidas.png")).getId();
            saveOnSale("나이키상품", 1000L, 5);
            Product adidasProduct = productRepository.save(Product.create(otherBrandId, "아디다스상품", "설명", 2000L, null));
            productStockRepository.save(ProductStock.create(adidasProduct.getId(), 5));

            ResponseEntity<ApiResponse<ProductV1Response.Page>> response = getAll("?brandId=" + brandId);

            assertAll(
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductV1Response.Detail::name)
                            .containsExactly("나이키상품"),
                    () -> assertThat(response.getBody().data().totalCount()).isEqualTo(1L)
            );
        }
    }
}
