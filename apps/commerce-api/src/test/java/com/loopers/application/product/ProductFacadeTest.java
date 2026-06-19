package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @Mock
    private ProductService productService;
    @Mock
    private BrandService brandService;
    @Mock
    private ProductCacheService productCacheService;

    @InjectMocks
    private ProductFacade productFacade;

    private static ProductModel product(Long brandId) {
        return new ProductModel(brandId, "상품명", "상품 설명", 10000L, 5, "image.jpg");
    }

    private static ProductModel productWithLikeCount(Long brandId, long likeCount) {
        ProductModel p = new ProductModel(brandId, "상품명", "상품 설명", 10000L, 5, "image.jpg");
        ReflectionTestUtils.setField(p, "likeCount", likeCount);
        return p;
    }

    private static BrandModel activeBrand() {
        return new BrandModel("브랜드", "설명", "brand.jpg");
    }

    private static BrandModel suspendedBrand() {
        BrandModel brand = new BrandModel("브랜드", "설명", "brand.jpg");
        brand.suspend();
        return brand;
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetProductsWithDetail {

        @DisplayName("캐시에 데이터가 있으면 DB 를 조회하지 않고 캐시에서 반환한다.")
        @Test
        void returns_cached_list_without_db_call() {
            ProductDetailInfo cached = new ProductDetailInfo(1L, 1L, "브랜드", "상품", "설명", 10000L, 5, "img.jpg", 7L, null);
            when(productCacheService.getProductList(null, ProductSortType.LATEST))
                .thenReturn(Optional.of(List.of(cached)));

            List<ProductDetailInfo> result = productFacade.getProductsWithDetail(null, ProductSortType.LATEST);

            assertThat(result).hasSize(1);
            verify(productService, never()).getActiveProducts(any(), any());
        }

        @DisplayName("캐시 미스 시 DB 에서 조회한 후 캐시에 저장한다.")
        @Test
        void fetches_from_db_and_caches_on_miss() {
            when(productCacheService.getProductList(null, ProductSortType.LATEST)).thenReturn(Optional.empty());
            ProductModel p = productWithLikeCount(1L, 7L);
            when(productService.getActiveProducts(null, ProductSortType.LATEST)).thenReturn(List.of(p));
            when(brandService.findBrand(1L)).thenReturn(Optional.of(activeBrand()));

            List<ProductDetailInfo> result = productFacade.getProductsWithDetail(null, ProductSortType.LATEST);

            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).likeCount()).isEqualTo(7L)
            );
            verify(productCacheService).cacheProductList(eq(null), eq(ProductSortType.LATEST), any());
        }

        @DisplayName("계약 중지된 브랜드의 상품은 목록에서 제외된다.")
        @Test
        void excludes_products_with_suspended_brand() {
            when(productCacheService.getProductList(null, ProductSortType.LATEST)).thenReturn(Optional.empty());
            ProductModel p = product(1L);
            when(productService.getActiveProducts(null, ProductSortType.LATEST)).thenReturn(List.of(p));
            when(brandService.findBrand(1L)).thenReturn(Optional.of(suspendedBrand()));

            List<ProductDetailInfo> result = productFacade.getProductsWithDetail(null, ProductSortType.LATEST);

            assertThat(result).isEmpty();
        }

        @DisplayName("삭제된 브랜드의 상품은 목록에서 제외된다.")
        @Test
        void excludes_products_with_deleted_brand() {
            when(productCacheService.getProductList(null, ProductSortType.LATEST)).thenReturn(Optional.empty());
            ProductModel p = product(1L);
            when(productService.getActiveProducts(null, ProductSortType.LATEST)).thenReturn(List.of(p));
            when(brandService.findBrand(1L)).thenReturn(Optional.empty());

            List<ProductDetailInfo> result = productFacade.getProductsWithDetail(null, ProductSortType.LATEST);

            assertThat(result).isEmpty();
        }

        @DisplayName("정렬 타입이 서비스 레이어로 전달되어 DB 정렬이 수행된다.")
        @Test
        void delegates_sort_to_service_layer() {
            when(productCacheService.getProductList(null, ProductSortType.PRICE_ASC)).thenReturn(Optional.empty());
            ProductModel cheap = new ProductModel(1L, "저렴", "설명", 1000L, 5, null);
            ProductModel expensive = new ProductModel(1L, "비싼", "설명", 9000L, 5, null);
            when(productService.getActiveProducts(null, ProductSortType.PRICE_ASC))
                .thenReturn(List.of(cheap, expensive));
            when(brandService.findBrand(1L)).thenReturn(Optional.of(activeBrand()));

            List<ProductDetailInfo> result = productFacade.getProductsWithDetail(null, ProductSortType.PRICE_ASC);

            assertThat(result.get(0).price()).isEqualTo(1000L);
            assertThat(result.get(1).price()).isEqualTo(9000L);
        }
    }

    @DisplayName("상품 상세를 조회할 때,")
    @Nested
    class GetProductWithDetail {

        @DisplayName("캐시에 데이터가 있으면 DB 를 조회하지 않고 반환한다.")
        @Test
        void returns_cached_detail_without_db_call() {
            ProductDetailInfo cached = new ProductDetailInfo(1L, 1L, "브랜드", "상품", "설명", 10000L, 5, "img.jpg", 3L, null);
            when(productCacheService.getProductDetail(1L)).thenReturn(Optional.of(cached));

            ProductDetailInfo result = productFacade.getProductWithDetail(1L);

            assertThat(result.likeCount()).isEqualTo(3L);
            verify(productService, never()).getProduct(any());
        }

        @DisplayName("캐시 미스 시 DB 에서 조회한 후 캐시에 저장한다.")
        @Test
        void fetches_from_db_and_caches_on_miss() {
            when(productCacheService.getProductDetail(0L)).thenReturn(Optional.empty());
            ProductModel p = productWithLikeCount(1L, 3L);
            when(productService.getProduct(0L)).thenReturn(p);
            when(brandService.findBrand(1L)).thenReturn(Optional.of(activeBrand()));

            ProductDetailInfo result = productFacade.getProductWithDetail(0L);

            assertAll(
                () -> assertThat(result.name()).isEqualTo("상품명"),
                () -> assertThat(result.brandName()).isEqualTo("브랜드"),
                () -> assertThat(result.likeCount()).isEqualTo(3L)
            );
            verify(productCacheService).cacheProductDetail(eq(0L), any());
        }

        @DisplayName("존재하지 않는 상품 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_not_found_when_product_not_exist() {
            when(productCacheService.getProductDetail(999L)).thenReturn(Optional.empty());
            when(productService.getProduct(999L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

            CoreException ex = assertThrows(CoreException.class,
                () -> productFacade.getProductWithDetail(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("계약 중지 브랜드 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_not_found_when_brand_is_suspended() {
            when(productCacheService.getProductDetail(0L)).thenReturn(Optional.empty());
            when(productService.getProduct(0L)).thenReturn(product(1L));
            when(brandService.findBrand(1L)).thenReturn(Optional.of(suspendedBrand()));

            CoreException ex = assertThrows(CoreException.class,
                () -> productFacade.getProductWithDetail(0L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class UpdateProduct {

        @DisplayName("캐시가 무효화된다.")
        @Test
        void evicts_cache_on_update() {
            ProductModel p = product(1L);
            when(productService.updateProduct(1L, "새 이름", "새 설명", 20000L, 10, "new.jpg")).thenReturn(p);

            productFacade.updateProduct(1L, "새 이름", "새 설명", 20000L, 10, "new.jpg");

            verify(productCacheService).evictProductDetail(1L);
            verify(productCacheService).evictAllProductLists();
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class DeleteProduct {

        @DisplayName("캐시가 무효화된다.")
        @Test
        void evicts_cache_on_delete() {
            productFacade.deleteProduct(1L);

            verify(productCacheService).evictProductDetail(1L);
            verify(productCacheService).evictAllProductLists();
        }
    }
}
