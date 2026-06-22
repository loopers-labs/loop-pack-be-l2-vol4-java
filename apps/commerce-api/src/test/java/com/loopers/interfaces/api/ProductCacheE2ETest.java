package com.loopers.interfaces.api;

import com.loopers.application.product.ProductCacheItem;
import com.loopers.application.product.ProductCacheService;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductLikeViewModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.stock.StockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductLikeViewJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.interfaces.api.product.ProductDto;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductCacheE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductCacheService productCacheService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductLikeViewJpaRepository productLikeViewJpaRepository;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품 상세 캐시 동작 검증")
    @Nested
    class DetailCache {

        @DisplayName("상품 상세 첫 조회 시, 캐시에 저장된다.")
        @Test
        void getProduct_storesCacheAfterFirstCall() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키"));
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, brand.getId()));
            productLikeViewJpaRepository.save(new ProductLikeViewModel(product.getId()));
            stockJpaRepository.save(new StockModel(product.getId(), 10));

            // act
            testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductResponse>>() {}
            );

            // assert — 캐시에 키가 존재해야 한다
            Optional<ProductCacheItem> cached = productCacheService.getDetail(product.getId());
            assertThat(cached).isPresent();
            assertThat(cached.get().name()).isEqualTo("에어포스1");
        }

        @DisplayName("캐시 히트 시, DB를 직접 수정해도 캐시된 값이 반환된다.")
        @Test
        void getProduct_returnsCachedValue_whenDbChangedDirectly() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키"));
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, brand.getId()));
            productLikeViewJpaRepository.save(new ProductLikeViewModel(product.getId()));
            stockJpaRepository.save(new StockModel(product.getId(), 10));

            // 첫 번째 조회 — 캐시 저장
            testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductResponse>>() {}
            );

            // Facade를 우회해 DB 직접 수정 — evict 미발생
            product.update("수정된이름", 139000L);
            productJpaRepository.save(product);

            // act — 두 번째 조회
            ResponseEntity<ApiResponse<ProductDto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // assert — 캐시된 이름(에어포스1)이 반환된다 (DB 수정값 아님)
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("에어포스1");
        }

        @DisplayName("상품 수정 시, 상세 캐시가 삭제되어 수정된 값이 반환된다.")
        @Test
        void updateProduct_evictsDetailCache() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키"));
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, brand.getId()));
            productLikeViewJpaRepository.save(new ProductLikeViewModel(product.getId()));
            stockJpaRepository.save(new StockModel(product.getId(), 10));

            // 첫 번째 조회 — 캐시 저장
            testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductResponse>>() {}
            );

            // admin API로 수정 — 캐시 evict 발생
            ProductDto.UpdateRequest updateRequest = new ProductDto.UpdateRequest("에어맥스90", 159000L);
            testRestTemplate.exchange(
                "/api-admin/v1/products/" + product.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest),
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductResponse>>() {}
            );

            // act — 세 번째 조회
            ResponseEntity<ApiResponse<ProductDto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // assert — 수정된 이름 반환 (캐시 evict 확인)
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("에어맥스90");
        }

        @DisplayName("상품 삭제 시, 상세 캐시가 삭제되어 404가 반환된다.")
        @Test
        void deleteProduct_evictsDetailCache() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키"));
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, brand.getId()));
            productLikeViewJpaRepository.save(new ProductLikeViewModel(product.getId()));
            stockJpaRepository.save(new StockModel(product.getId(), 10));

            // 첫 번째 조회 — 캐시 저장
            testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductResponse>>() {}
            );

            // admin API로 삭제 — 캐시 evict 발생
            testRestTemplate.exchange(
                "/api-admin/v1/products/" + product.getId(),
                HttpMethod.DELETE, null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // act — 삭제 후 조회
            ResponseEntity<ApiResponse<ProductDto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId(),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // assert — 캐시 evict 후 DB 조회 → 404
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록 캐시 동작 검증")
    @Nested
    class ListCache {

        @DisplayName("필터 없는 목록 첫 조회 시, 캐시에 저장된다.")
        @Test
        void getProducts_storesCacheForUnfilteredRequest() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키"));
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, brand.getId()));
            productLikeViewJpaRepository.save(new ProductLikeViewModel(product.getId()));
            stockJpaRepository.save(new StockModel(product.getId(), 10));

            // act — 필터 없는 목록 조회
            testRestTemplate.exchange(
                "/api/v1/products?sort=LATEST&page=0",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductPageResponse>>() {}
            );

            // assert — 캐시에 저장되어야 한다
            Optional<List<ProductInfo>> cached = productCacheService.getList(ProductSort.LATEST, 0);
            assertThat(cached).isPresent();
        }

        @DisplayName("필터가 있는 목록 조회 시, 캐시를 사용하지 않는다.")
        @Test
        void getProducts_doesNotCache_whenFilterApplied() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키"));
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, brand.getId()));
            productLikeViewJpaRepository.save(new ProductLikeViewModel(product.getId()));
            stockJpaRepository.save(new StockModel(product.getId(), 10));

            // act — brandId 필터 목록 조회
            testRestTemplate.exchange(
                "/api/v1/products?brandId=" + brand.getId() + "&sort=LATEST&page=0",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductPageResponse>>() {}
            );

            // assert — 캐시에 저장되지 않아야 한다
            Optional<List<ProductInfo>> cached = productCacheService.getList(ProductSort.LATEST, 0);
            assertThat(cached).isEmpty();
        }

        @DisplayName("상품 등록 시, 목록 캐시가 모두 삭제된다.")
        @Test
        void createProduct_evictsListCache() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키"));
            ProductModel product = productJpaRepository.save(new ProductModel("에어포스1", 139000L, brand.getId()));
            productLikeViewJpaRepository.save(new ProductLikeViewModel(product.getId()));
            stockJpaRepository.save(new StockModel(product.getId(), 10));

            // 목록 조회 — 캐시 저장
            testRestTemplate.exchange(
                "/api/v1/products?sort=LATEST&page=0",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductPageResponse>>() {}
            );
            assertThat(productCacheService.getList(ProductSort.LATEST, 0)).isPresent();

            // act — 새 상품 등록 (목록 캐시 evict 발생)
            ProductDto.CreateRequest createRequest = new ProductDto.CreateRequest("에어맥스90", 159000L, brand.getId(), 5);
            testRestTemplate.exchange(
                "/api-admin/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(createRequest),
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductResponse>>() {}
            );

            // assert — 목록 캐시 전체 삭제
            for (ProductSort sort : ProductSort.values()) {
                for (int page = 0; page < 3; page++) {
                    assertThat(productCacheService.getList(sort, page)).isEmpty();
                }
            }
        }
    }
}
