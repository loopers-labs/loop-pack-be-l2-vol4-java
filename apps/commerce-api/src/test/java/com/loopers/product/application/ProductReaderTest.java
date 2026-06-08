package com.loopers.product.application;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.product.domain.ProductErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductReaderTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductReader productReader = new ProductReader(productRepository);

    @Test
    @DisplayName("ensureActiveExists 는 판매중 상품이 존재하면 예외 없이 통과한다")
    void givenActiveProductId_whenEnsureActiveExists_thenDoesNotThrow() {
        when(productRepository.existsActiveById(1L)).thenReturn(true);

        assertThatCode(() -> productReader.ensureActiveExists(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ensureActiveExists 는 판매중 상품이 없으면 NOT_FOUND 예외가 발생한다")
    void givenNonActiveProductId_whenEnsureActiveExists_thenThrowsNotFound() {
        when(productRepository.existsActiveById(999L)).thenReturn(false);

        assertThatThrownBy(() -> productReader.ensureActiveExists(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("getInfo 는 판매중 상품의 name·brandId·price projection 을 반환한다")
    void givenActiveProductId_whenGetInfo_thenReturnsProjection() {
        Product product = Product.create(7L, "셔츠", "설명", 29_000L, "thumb.jpg");
        when(productRepository.findActiveById(1L)).thenReturn(Optional.of(product));

        ProductInfo result = productReader.getInfo(1L);

        assertAll(
                () -> assertThat(result.name()).isEqualTo("셔츠"),
                () -> assertThat(result.brandId()).isEqualTo(7L),
                () -> assertThat(result.price()).isEqualTo(29_000L)
        );
    }

    @Test
    @DisplayName("getInfo 는 판매중이 아니거나 없으면 NOT_FOUND 예외가 발생한다")
    void givenNonActiveProductId_whenGetInfo_thenThrowsNotFound() {
        when(productRepository.findActiveById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productReader.getInfo(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_FOUND);
    }
}
