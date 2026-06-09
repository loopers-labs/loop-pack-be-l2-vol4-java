package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDomainServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductDomainService productDomainService;

    private static ProductModel product(Long brandId) {
        return new ProductModel(brandId, "상품명", "상품 설명", 10000L, 5, "image.jpg");
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class DeductStock {

        @DisplayName("충분한 재고가 있으면 차감 후 상품을 반환한다.")
        @Test
        void deducts_stock_successfully() {
            ProductModel p = product(1L);
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
            ProductModel p = product(1L);
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
            p.deductStock(1);
            when(productRepository.find(0L)).thenReturn(Optional.of(p));

            ProductModel result = productDomainService.restoreStock(0L, 3);

            assertAll(
                () -> assertThat(result.getStock()).isEqualTo(3),
                () -> assertThat(result.isSoldOut()).isFalse()
            );
        }
    }
}
