package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long PRODUCT_ID = 1L;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private ProductModel sampleProduct() {
        return new ProductModel(new BrandModel("Loopers", "감성"), "후드", "포근함", 49_000L);
    }

    @DisplayName("ID로 단건 조회 시")
    @Nested
    class GetById {

        @DisplayName("존재하는 상품이면 그대로 반환한다")
        @Test
        void returnsProduct_whenIdExists() {
            // given
            ProductModel product = sampleProduct();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

            // when
            ProductModel result = productService.getById(PRODUCT_ID);

            // then
            assertThat(result).isSameAs(product);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            // given
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> productService.getById(PRODUCT_ID));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("목록 조회 시")
    @Nested
    class Search {

        @DisplayName("Repository가 반환한 Page를 그대로 전달한다")
        @Test
        void returnsPage_fromRepository() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<ProductModel> page = new PageImpl<>(List.of(sampleProduct()), pageable, 1);
            when(productRepository.search(null, SortOption.LATEST, pageable)).thenReturn(page);

            // when
            Page<ProductModel> result = productService.search(null, SortOption.LATEST, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요 수 증가 시")
    @Nested
    class IncrementLikeCount {

        @DisplayName("상품이 존재하면 likeCount가 증가한다")
        @Test
        void increasesLikeCount_whenProductExists() {
            // given
            ProductModel product = sampleProduct();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

            // when
            productService.incrementLikeCount(PRODUCT_ID);

            // then
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("상품이 없으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // given
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> productService.incrementLikeCount(PRODUCT_ID));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("좋아요 수 감소 시")
    @Nested
    class DecrementLikeCount {

        @DisplayName("상품이 존재하면 likeCount가 감소한다")
        @Test
        void decreasesLikeCount_whenProductExists() {
            // given
            ProductModel product = sampleProduct();
            product.increaseLike();
            product.increaseLike();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

            // when
            productService.decrementLikeCount(PRODUCT_ID);

            // then
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("상품이 없으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // given
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> productService.decrementLikeCount(PRODUCT_ID));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
