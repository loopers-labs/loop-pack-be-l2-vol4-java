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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @DisplayName("활성 상품 조회 시, ")
    @Nested
    class GetActive {

        @DisplayName("존재하는 active 상품이면, 해당 상품을 반환한다.")
        @Test
        void returnsProduct_whenActiveProductExists() {
            // given
            Long productId = 1L;
            ProductModel product = new ProductModel("에어포스1", "흰색 운동화", 139_000L, 10L);
            given(productRepository.findActive(productId)).willReturn(Optional.of(product));

            // when
            ProductModel result = productService.getActive(productId);

            // then
            assertThat(result).isSameAs(product);
        }

        @DisplayName("존재하지 않거나 soft-deleted 상품이면, PRODUCT_NOT_FOUND 예외를 던진다.")
        @Test
        void throwsProductNotFound_whenProductIsAbsentOrSoftDeleted() {
            // given
            Long productId = 999L;
            given(productRepository.findActive(productId)).willReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> productService.getActive(productId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND);
        }
    }
}
