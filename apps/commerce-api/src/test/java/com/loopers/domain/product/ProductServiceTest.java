package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private ProductService productService;
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productService = new ProductService(productRepository);
    }

    @DisplayName("상품을 ID로 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 ID라면 상품이 반환된다.")
        @Test
        void returnsProduct_whenProductExists() {
            // given
            Long id = 1L;
            ProductModel product = new ProductModel(10L, "테스트 상품", BigDecimal.valueOf(1000));
            when(productRepository.find(id)).thenReturn(Optional.of(product));

            // when
            ProductModel result = productService.getById(id);

            // then
            assertThat(result).isSameAs(product);
        }

        @DisplayName("존재하지 않는 ID라면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // given
            Long id = 999L;
            when(productRepository.find(id)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () -> productService.getById(id));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetAll {

        @DisplayName("정렬 기준별로 상품 목록이 반환된다.")
        @ParameterizedTest
        @EnumSource(SortType.class)
        void returnsProducts_whenProductsExist(SortType sortType) {
            // given
            List<ProductModel> products = List.of(
                    new ProductModel(10L, "상품A", BigDecimal.valueOf(2000)),
                    new ProductModel(10L, "상품B", BigDecimal.valueOf(1000))
            );
            when(productRepository.findAll(sortType)).thenReturn(products);

            // when
            List<ProductModel> result = productService.findAll(sortType);

            // then
            assertThat(result).isSameAs(products);
        }

        @DisplayName("상품이 없으면 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoProductsExist() {
            // given
            when(productRepository.findAll(SortType.LATEST)).thenReturn(List.of());

            // when
            List<ProductModel> result = productService.findAll(SortType.LATEST);

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정상 입력이라면 저장된 상품이 반환된다.")
        @Test
        void createsProduct_whenValidInputIsProvided() {
            // given
            ProductModel expected = new ProductModel(1L, "테스트 상품", BigDecimal.valueOf(1000));
            when(productRepository.save(any(ProductModel.class))).thenReturn(expected);

            // when
            ProductModel result = productService.create(1L, "테스트 상품", BigDecimal.valueOf(1000));

            // then
            assertThat(result).isSameAs(expected);
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("존재하는 상품이라면 저장된 상품이 반환된다.")
        @Test
        void updatesProduct_whenProductExists() {
            // given
            Long id = 1L;
            ProductModel product = new ProductModel(10L, "원래 상품", BigDecimal.valueOf(1000));
            when(productRepository.find(id)).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);

            // when
            ProductModel result = productService.update(id, "수정된 상품", BigDecimal.valueOf(2000));

            // then
            assertThat(result).isSameAs(product);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("존재하는 상품이라면 예외 없이 완료된다.")
        @Test
        void deletesProduct_whenProductExists() {
            // given
            Long id = 1L;
            ProductModel product = new ProductModel(10L, "상품", BigDecimal.valueOf(1000));
            when(productRepository.find(id)).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);

            // when & then
            assertDoesNotThrow(() -> productService.delete(id));
        }
    }
}
