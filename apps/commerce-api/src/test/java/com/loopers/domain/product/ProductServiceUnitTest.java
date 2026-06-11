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
class ProductServiceUnitTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductStockService productStockService;

    @InjectMocks
    private ProductService productService;

    @DisplayName("상품 단건 조회할 때")
    @Nested
    class GetProduct {

        @DisplayName("존재한다면, 상품 정보를 반환한다.")
        @Test
        void returnsProduct_whenExists() {
            // given
            ProductModel product = ProductModel.of(
                    1L,
                    ProductName.of("티셔츠"),
                    ProductDescription.of("면 100%"),
                    ProductPrice.of(10000L)
            );
            given(productRepository.find(1L)).willReturn(Optional.of(product));

            // when
            ProductModel result = productService.getProduct(1L);

            // then
            assertThat(result.getName().value()).isEqualTo("티셔츠");
            assertThat(result.getPrice().value()).isEqualTo(10000L);
        }

        @DisplayName("존재하지 않거나 삭제되었다면, 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // given
            given(productRepository.find(1L)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productService.getProduct(1L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}