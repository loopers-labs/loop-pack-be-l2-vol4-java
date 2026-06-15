package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.SortOption;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ProductFacade 통합 — 고객 노출 형태(available bool) 변환과 Product+Brand+Stock 합성을 검증한다.
 * Admin Facade는 stockQuantity 정수를 노출하지만, 고객은 boolean으로 단순화된다.
 */
@SpringBootTest
class ProductFacadeIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private Long brandId;
    private Long inStockProductId;
    private Long outOfStockProductId;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        brandId = brand.getId();

        ProductModel inStock = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L));
        ProductModel outOfStock = productRepository.save(new ProductModel(brand.getId(), "맨투맨", "심플", 30_000L));
        inStockProductId = inStock.getId();
        outOfStockProductId = outOfStock.getId();

        stockRepository.save(new StockModel(inStockProductId, 10));
        stockRepository.save(new StockModel(outOfStockProductId, 0));

        productService.incrementLikeCount(inStockProductId);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("상품 상세 조회 시")
    @Nested
    class GetProductDetail {

        @DisplayName("재고가 있으면 available=true와 함께 브랜드명·좋아요 수가 합성되어 반환된다")
        @Test
        void returnsAvailableTrue_withBrandAndLikes() {
            // when
            ProductInfo info = productFacade.getProductDetail(inStockProductId);

            // then
            assertAll(
                () -> assertThat(info.id()).isEqualTo(inStockProductId),
                () -> assertThat(info.name()).isEqualTo("후드"),
                () -> assertThat(info.brandId()).isEqualTo(brandId),
                () -> assertThat(info.brandName()).isEqualTo("Loopers"),
                () -> assertThat(info.likeCount()).isEqualTo(1L),
                () -> assertThat(info.available()).isTrue()
            );
        }

        @DisplayName("재고가 0이면 available=false로 반환된다 (수량 정수가 아니라 boolean으로 단순화)")
        @Test
        void returnsAvailableFalse_whenStockIsZero() {
            // when
            ProductInfo info = productFacade.getProductDetail(outOfStockProductId);

            // then
            assertThat(info.available()).isFalse();
        }

        @DisplayName("상품이 존재하지 않으면 NOT_FOUND가 발생한다")
        @Test
        void throwsNotFound_whenProductMissing() {
            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> productFacade.getProductDetail(99_999L));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("캐시 적용 시")
    @Nested
    class Caching {

        @DisplayName("상세를 캐싱한 뒤 좋아요 수가 늘어도 TTL 동안은 캐시된 옛 값이 반환된다")
        @Test
        void servesStaleLikeCount_withinTtl() {
            // given - 첫 조회로 likeCount=1 캐싱
            ProductInfo first = productFacade.getProductDetail(inStockProductId);
            assertThat(first.likeCount()).isEqualTo(1L);

            // when - DB의 likeCount는 2로 증가하지만 캐시는 무효화하지 않는다
            productService.incrementLikeCount(inStockProductId);
            ProductInfo cached = productFacade.getProductDetail(inStockProductId);

            // then - 캐시 히트라 옛 값(1) 유지 (정확도 계약: TTL 동안 stale 허용)
            assertThat(cached.likeCount()).isEqualTo(1L);
        }
    }

    @DisplayName("상품 목록 조회 시")
    @Nested
    class Search {

        @DisplayName("재고 0 상품은 available=false, 재고가 있는 상품은 available=true로 페이지에 함께 노출된다")
        @Test
        void mapsAvailable_perItem() {
            // when
            Page<ProductInfo> page = productFacade.search(brandId, SortOption.PRICE_ASC, PageRequest.of(0, 20));

            // then
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(2),
                () -> assertThat(page.getContent())
                    .filteredOn(p -> p.id().equals(inStockProductId))
                    .singleElement().extracting(ProductInfo::available).isEqualTo(true),
                () -> assertThat(page.getContent())
                    .filteredOn(p -> p.id().equals(outOfStockProductId))
                    .singleElement().extracting(ProductInfo::available).isEqualTo(false)
            );
        }

        @DisplayName("상품은 살아있는데 참조 브랜드가 soft-delete된 비대칭 상태에선 INTERNAL_ERROR로 fail-fast한다")
        @Test
        void throwsInternalError_whenReferencedBrandIsSoftDeleted() {
            // given - 브랜드를 soft-delete (상품은 brandId로만 참조)
            BrandModel brand = brandRepository.findById(brandId).orElseThrow();
            brand.delete();
            brandRepository.save(brand);

            // when / then
            CoreException ex = assertThrows(CoreException.class,
                () -> productFacade.search(brandId, SortOption.PRICE_ASC, PageRequest.of(0, 20)));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        }
    }
}
