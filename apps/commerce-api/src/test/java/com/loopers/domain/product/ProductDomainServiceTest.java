package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDomainServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private ProductDomainService productDomainService;

    private static ProductModel product(Long brandId) {
        return new ProductModel(brandId, "상품명", "상품 설명", 10000L, 5, "image.jpg");
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

        @DisplayName("활성 브랜드 상품은 브랜드 정보와 좋아요 수를 포함해 반환된다.")
        @Test
        void returns_product_detail_with_brand_and_like_count() {
            ProductModel p = product(1L);
            when(productRepository.findAllActive(null)).thenReturn(List.of(p));
            when(brandRepository.find(1L)).thenReturn(Optional.of(activeBrand()));
            when(likeRepository.countActiveByProductId(any())).thenReturn(7L);

            List<ProductWithDetail> result = productDomainService.getProductsWithDetail(null, ProductSortType.LATEST);

            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).brand().getName()).isEqualTo("브랜드"),
                () -> assertThat(result.get(0).likeCount()).isEqualTo(7L)
            );
        }

        @DisplayName("계약 중지된 브랜드의 상품은 목록에서 제외된다.")
        @Test
        void excludes_products_with_suspended_brand() {
            ProductModel p = product(1L);
            when(productRepository.findAllActive(null)).thenReturn(List.of(p));
            when(brandRepository.find(1L)).thenReturn(Optional.of(suspendedBrand()));

            List<ProductWithDetail> result = productDomainService.getProductsWithDetail(null, ProductSortType.LATEST);

            assertThat(result).isEmpty();
        }

        @DisplayName("삭제된 브랜드의 상품은 목록에서 제외된다.")
        @Test
        void excludes_products_with_deleted_brand() {
            ProductModel p = product(1L);
            when(productRepository.findAllActive(null)).thenReturn(List.of(p));
            when(brandRepository.find(1L)).thenReturn(Optional.empty());

            List<ProductWithDetail> result = productDomainService.getProductsWithDetail(null, ProductSortType.LATEST);

            assertThat(result).isEmpty();
        }

        @DisplayName("PRICE_ASC 정렬 시 가격 오름차순으로 반환된다.")
        @Test
        void returns_sorted_by_price_asc() {
            ProductModel cheap = new ProductModel(1L, "저렴", "설명", 1000L, 5, null);
            ProductModel expensive = new ProductModel(1L, "비싼", "설명", 9000L, 5, null);
            BrandModel brand = activeBrand();

            when(productRepository.findAllActive(null)).thenReturn(List.of(expensive, cheap));
            when(brandRepository.find(1L)).thenReturn(Optional.of(brand));
            when(likeRepository.countActiveByProductId(any())).thenReturn(0L);

            List<ProductWithDetail> result = productDomainService.getProductsWithDetail(null, ProductSortType.PRICE_ASC);

            assertThat(result.get(0).product().getPrice()).isEqualTo(1000L);
            assertThat(result.get(1).product().getPrice()).isEqualTo(9000L);
        }

        @DisplayName("LIKES_DESC 정렬 시 좋아요 수 내림차순으로 반환된다.")
        @Test
        void returns_sorted_by_likes_desc() {
            ProductModel p1 = product(1L);
            ProductModel p2 = product(1L);
            BrandModel brand = activeBrand();

            when(productRepository.findAllActive(null)).thenReturn(List.of(p1, p2));
            when(brandRepository.find(1L)).thenReturn(Optional.of(brand));
            when(likeRepository.countActiveByProductId(any()))
                .thenReturn(5L)
                .thenReturn(20L);

            List<ProductWithDetail> result = productDomainService.getProductsWithDetail(null, ProductSortType.LIKES_DESC);

            assertThat(result.get(0).likeCount()).isEqualTo(20L);
            assertThat(result.get(1).likeCount()).isEqualTo(5L);
        }
    }

    @DisplayName("상품 상세를 조회할 때,")
    @Nested
    class GetProductWithDetail {

        @DisplayName("활성 상품이면 브랜드 정보와 좋아요 수를 포함해 반환된다.")
        @Test
        void returns_detail_when_product_and_brand_are_active() {
            ProductModel p = product(1L);
            when(productRepository.find(0L)).thenReturn(Optional.of(p));
            when(brandRepository.find(1L)).thenReturn(Optional.of(activeBrand()));
            when(likeRepository.countActiveByProductId(any())).thenReturn(3L);

            ProductWithDetail result = productDomainService.getProductWithDetail(0L);

            assertAll(
                () -> assertThat(result.product()).isEqualTo(p),
                () -> assertThat(result.brand().getName()).isEqualTo("브랜드"),
                () -> assertThat(result.likeCount()).isEqualTo(3L)
            );
        }

        @DisplayName("존재하지 않는 상품 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_not_found_when_product_not_exist() {
            when(productRepository.find(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class,
                () -> productDomainService.getProductWithDetail(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("계약 중지 브랜드 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_not_found_when_brand_is_suspended() {
            when(productRepository.find(0L)).thenReturn(Optional.of(product(1L)));
            when(brandRepository.find(1L)).thenReturn(Optional.of(suspendedBrand()));

            CoreException ex = assertThrows(CoreException.class,
                () -> productDomainService.getProductWithDetail(0L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class DeductStock {

        @DisplayName("충분한 재고가 있으면 차감 후 상품을 반환한다.")
        @Test
        void deducts_stock_successfully() {
            ProductModel p = product(1L); // stock=5
            when(productRepository.findWithLock(0L)).thenReturn(Optional.of(p));

            ProductModel result = productDomainService.deductStock(0L, 3);

            assertThat(result.getStock()).isEqualTo(2);
            verify(productRepository).findWithLock(0L);
        }

        @DisplayName("비관적 락(findWithLock)으로 상품을 조회한다.")
        @Test
        void uses_pessimistic_lock() {
            when(productRepository.findWithLock(0L)).thenReturn(Optional.of(product(1L)));

            productDomainService.deductStock(0L, 1);

            verify(productRepository).findWithLock(0L);
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_not_found_when_product_not_exist() {
            when(productRepository.findWithLock(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class,
                () -> productDomainService.deductStock(999L, 1));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고보다 많은 수량을 차감하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_bad_request_when_stock_is_insufficient() {
            ProductModel p = product(1L); // stock=5
            when(productRepository.findWithLock(0L)).thenReturn(Optional.of(p));

            CoreException ex = assertThrows(CoreException.class,
                () -> productDomainService.deductStock(0L, 10));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 복구할 때,")
    @Nested
    class RestoreStock {

        @DisplayName("재고가 정상적으로 복구된다.")
        @Test
        void restores_stock_successfully() {
            ProductModel p = new ProductModel(1L, "상품명", "설명", 10000L, 1, null);
            p.deductStock(1); // 품절 처리
            when(productRepository.find(0L)).thenReturn(Optional.of(p));

            ProductModel result = productDomainService.restoreStock(0L, 3);

            assertAll(
                () -> assertThat(result.getStock()).isEqualTo(3),
                () -> assertThat(result.isSoldOut()).isFalse()
            );
        }
    }
}