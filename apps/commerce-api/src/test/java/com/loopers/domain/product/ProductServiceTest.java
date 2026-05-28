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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private static ProductModel product() {
        return new ProductModel(1L, "상품명", "상품 설명", 10000L, 5, "image.jpg");
    }

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성하면 저장된 상품이 반환된다.")
        @Test
        void returns_saved_product_when_valid_inputs() {
            ProductModel saved = product();
            when(productRepository.save(any(ProductModel.class))).thenReturn(saved);

            ProductModel result = productService.createProduct(1L, "상품명", "상품 설명", 10000L, 5, "image.jpg");

            assertThat(result).isEqualTo(saved);
            verify(productRepository).save(any(ProductModel.class));
        }
    }

    @DisplayName("상품을 단건 조회할 때,")
    @Nested
    class Get {

        @DisplayName("존재하는 ID를 주면 상품을 반환한다.")
        @Test
        void returns_product_when_id_exists() {
            ProductModel saved = product();
            when(productRepository.find(0L)).thenReturn(Optional.of(saved));

            ProductModel result = productService.getProduct(0L);

            assertThat(result).isEqualTo(saved);
        }

        @DisplayName("존재하지 않는 ID를 주면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_when_id_not_found() {
            when(productRepository.find(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class,
                () -> productService.getProduct(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("전체 조회 시 repository에서 반환된 목록을 그대로 반환한다.")
        @Test
        void returns_all_products() {
            List<ProductModel> products = List.of(product(), product());
            when(productRepository.findAll()).thenReturn(products);

            List<ProductModel> result = productService.getAllProducts();

            assertThat(result).hasSize(2);
        }
    }

    @DisplayName("상품을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("존재하는 상품의 정보를 수정하면 업데이트된 상품이 반환된다.")
        @Test
        void updates_product_when_id_exists() {
            ProductModel saved = product();
            when(productRepository.find(0L)).thenReturn(Optional.of(saved));

            ProductModel result = productService.updateProduct(0L, "새 상품명", "새 설명", 20000L, 10, "new.jpg");

            assertThat(result.getName()).isEqualTo("새 상품명");
            assertThat(result.getPrice()).isEqualTo(20000L);
        }

        @DisplayName("존재하지 않는 상품을 수정하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_when_id_not_found() {
            when(productRepository.find(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class,
                () -> productService.updateProduct(999L, "이름", "설명", 1000L, 1, null));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("존재하는 상품을 삭제하면 repository.delete가 호출된다.")
        @Test
        void calls_delete_when_id_exists() {
            when(productRepository.find(0L)).thenReturn(Optional.of(product()));

            productService.deleteProduct(0L);

            verify(productRepository).delete(0L);
        }

        @DisplayName("존재하지 않는 상품을 삭제하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_when_id_not_found() {
            when(productRepository.find(999L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class,
                () -> productService.deleteProduct(999L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}