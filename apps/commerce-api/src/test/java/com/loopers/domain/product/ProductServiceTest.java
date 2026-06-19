package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    private ProductStatsService productStatsService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productStatsService = mock(ProductStatsService.class);
        productService = new ProductService(productRepository, productStatsService);
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

    @DisplayName("ID 목록으로 상품을 조회할 때,")
    @Nested
    class FindAllByIdsOrThrow {

        @DisplayName("모든 ID에 해당하는 상품이 존재하면 상품 목록이 반환된다.")
        @Test
        void returnsProducts_whenAllIdsExist() {
            // given
            List<Long> ids = List.of(1L, 2L);
            List<ProductModel> products = List.of(
                    new ProductModel(10L, "상품A", BigDecimal.valueOf(1000)),
                    new ProductModel(10L, "상품B", BigDecimal.valueOf(2000))
            );
            when(productRepository.findAllByIds(ids)).thenReturn(products);

            // when
            List<ProductModel> result = productService.findAllByIdsOrThrow(ids);

            // then
            assertThat(result).hasSize(2);
        }

        @DisplayName("일부 ID에 해당하는 상품이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenAnyIdIsMissing() {
            // given
            List<Long> ids = List.of(1L, 2L);
            List<ProductModel> found = List.of(
                    new ProductModel(10L, "상품A", BigDecimal.valueOf(1000))
            );
            when(productRepository.findAllByIds(ids)).thenReturn(found);

            // when
            CoreException result = assertThrows(CoreException.class, () -> productService.findAllByIdsOrThrow(ids));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
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

    @DisplayName("ID 목록으로 상품을 단순 조회할 때,")
    @Nested
    class FindAllByIds {

        @DisplayName("해당 ID 목록의 상품 목록이 반환된다.")
        @Test
        void returnsProducts_whenIdsAreProvided() {
            // given
            List<Long> ids = List.of(1L, 2L);
            List<ProductModel> products = List.of(
                    new ProductModel(10L, "상품A", BigDecimal.valueOf(1000)),
                    new ProductModel(10L, "상품B", BigDecimal.valueOf(2000))
            );
            when(productRepository.findAllByIds(ids)).thenReturn(products);

            // when
            List<ProductModel> result = productService.findAllByIds(ids);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @DisplayName("브랜드 ID로 상품 목록을 조회할 때,")
    @Nested
    class FindAllByBrandId {

        @DisplayName("해당 브랜드의 상품 목록이 반환된다.")
        @Test
        void returnsProducts_whenBrandIdIsProvided() {
            // given
            Long brandId = 1L;
            List<ProductModel> products = List.of(
                    new ProductModel(brandId, "상품A", BigDecimal.valueOf(1000))
            );
            when(productRepository.findAllByBrandId(brandId)).thenReturn(products);

            // when
            List<ProductModel> result = productService.findAllByBrandId(brandId);

            // then
            assertThat(result).hasSize(1);
        }

        @DisplayName("페이지네이션 조회 시 해당 브랜드의 상품 페이지가 반환된다.")
        @Test
        void returnsProductPage_whenBrandIdAndPageableAreProvided() {
            // given
            Long brandId = 1L;
            Pageable pageable = PageRequest.of(0, 20);
            ProductModel product = new ProductModel(brandId, "상품A", BigDecimal.valueOf(1000));
            Page<ProductModel> page = new PageImpl<>(List.of(product));
            when(productRepository.findAllByBrandId(brandId, pageable)).thenReturn(page);

            // when
            Page<ProductModel> result = productService.findAllByBrandId(brandId, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @DisplayName("상품 동적 목록을 조회할 때,")
    @Nested
    class FindProducts {

        @DisplayName("조건에 맞는 상품 페이지가 반환된다.")
        @Test
        void returnsProductPage_whenConditionIsProvided() {
            // given
            Long brandId = 1L;
            Pageable pageable = PageRequest.of(0, 20);
            ProductModel product = new ProductModel(brandId, "상품A", BigDecimal.valueOf(1000));
            Page<ProductModel> page = new PageImpl<>(List.of(product));
            when(productRepository.findProducts(brandId, pageable)).thenReturn(page);

            // when
            Page<ProductModel> result = productService.findProducts(brandId, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
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

        @DisplayName("존재하지 않는 상품이라면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // given
            Long id = 999L;
            when(productRepository.find(id)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productService.update(id, "수정된 상품", BigDecimal.valueOf(2000)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
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

        @DisplayName("존재하지 않는 상품이라면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // given
            Long id = 999L;
            when(productRepository.find(id)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () -> productService.delete(id));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
