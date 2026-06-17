package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.FakeBrandRepository;
import com.loopers.domain.product.FakeProductRepository;
import com.loopers.domain.product.ProductDetailService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductFacade 의 캐시 흐름 검증 — HIT / MISS / EVICT.
 * <p>
 * Redis 어댑터의 실제 동작은 별도 테스트(ProductRedisCacheAdapterTest) 에서 다룬다.
 * 여기서는 Facade 가 Port 에 올바른 순서/조건으로 호출하는지만 검증한다.
 */
class ProductFacadeCacheTest {

    private FakeProductCachePort fakeCache;
    private ProductFacade facade;
    private ProductService productService;
    private BrandService brandService;
    private Long brandId;
    private ProductModel product;

    @BeforeEach
    void setUp() {
        FakeProductRepository fakeProductRepo = new FakeProductRepository();
        FakeBrandRepository fakeBrandRepo = new FakeBrandRepository();
        productService = new ProductService(fakeProductRepo);
        brandService = new BrandService(fakeBrandRepo);
        ProductDetailService detailService = new ProductDetailService(productService, brandService);
        fakeCache = new FakeProductCachePort();
        facade = new ProductFacade(productService, brandService, detailService, fakeCache);

        brandId = brandService.createBrand("나이키", "스포츠").getId();
        product = productService.createProduct(brandId, "에어맥스", "런닝화", 1000L, 10, null);
    }

    @DisplayName("상품 상세")
    @Nested
    class Detail {

        @DisplayName("첫 호출은 MISS 다 — DB 조회 후 캐시에 적재된다.")
        @Test
        void firstCall_isMiss_andCachesResult() {
            // act
            ProductDetailInfo result = facade.getProductDetail(product.getId());

            // assert
            assertThat(result.productName()).isEqualTo("에어맥스");
            assertThat(fakeCache.detailMisses).isEqualTo(1);
            assertThat(fakeCache.detailHits).isZero();
            assertThat(fakeCache.containsDetail(product.getId())).isTrue();
        }

        @DisplayName("두 번째 호출은 HIT 다 — DB 를 거치지 않고 캐시에서 반환된다.")
        @Test
        void secondCall_isHit() {
            // arrange
            facade.getProductDetail(product.getId());

            // act
            facade.getProductDetail(product.getId());

            // assert
            assertThat(fakeCache.detailMisses).isEqualTo(1);
            assertThat(fakeCache.detailHits).isEqualTo(1);
        }
    }

    @DisplayName("상품 목록")
    @Nested
    class List {

        @DisplayName("첫 호출은 MISS, 두 번째 호출은 HIT 이다.")
        @Test
        void firstCallMiss_secondCallHit() {
            // arrange
            ProductCriteria.List criteria = new ProductCriteria.List(ProductSortType.LIKES_DESC, brandId);

            // act
            facade.listProducts(criteria);
            facade.listProducts(criteria);

            // assert — 캐시에 적재되어 있음
            assertThat(fakeCache.containsList(brandId, ProductSortType.LIKES_DESC)).isTrue();
        }

        @DisplayName("sortType 이 null 이면 LATEST 로 정규화되어 캐시 키가 분리된다.")
        @Test
        void nullSort_isNormalizedToLatest() {
            // arrange
            ProductCriteria.List criteria = new ProductCriteria.List(null, brandId);

            // act
            facade.listProducts(criteria);

            // assert — LATEST 키에 적재
            assertThat(fakeCache.containsList(brandId, ProductSortType.LATEST)).isTrue();
        }

        @DisplayName("brandId 가 null 이면 'all' 스코프 키로 적재된다.")
        @Test
        void nullBrand_isCachedUnderAllScope() {
            // arrange
            ProductCriteria.List criteria = new ProductCriteria.List(ProductSortType.LIKES_DESC, null);

            // act
            facade.listProducts(criteria);

            // assert
            assertThat(fakeCache.containsList(null, ProductSortType.LIKES_DESC)).isTrue();
        }
    }

    @DisplayName("상품 생성")
    @Nested
    class Create {

        @DisplayName("새 상품이 등록되면, 해당 브랜드 + 전체 스코프 목록 캐시가 무효화된다.")
        @Test
        void evictsListsByBrandAndAll() {
            // arrange — 두 스코프 캐시 워밍업
            facade.listProducts(new ProductCriteria.List(ProductSortType.LIKES_DESC, brandId));
            facade.listProducts(new ProductCriteria.List(ProductSortType.LIKES_DESC, null));
            assertThat(fakeCache.containsList(brandId, ProductSortType.LIKES_DESC)).isTrue();
            assertThat(fakeCache.containsList(null, ProductSortType.LIKES_DESC)).isTrue();

            // act
            facade.createProduct(new ProductCriteria.Create(brandId, "에어조던", "농구화", 200_000L, 5, null));

            // assert — 두 스코프 모두 evict
            assertThat(fakeCache.containsList(brandId, ProductSortType.LIKES_DESC)).isFalse();
            assertThat(fakeCache.containsList(null, ProductSortType.LIKES_DESC)).isFalse();
            assertThat(fakeCache.listEvictsByBrand).isEqualTo(2);
        }
    }
}
