package com.loopers.product.application;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductReaderTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductStockRepository productStockRepository = mock(ProductStockRepository.class);
    private final ProductReader productReader = new ProductReader(productRepository, productStockRepository);

    @Test
    @DisplayName("존재하는 productId 로 조회하면 해당 상품을 반환한다")
    void givenExistingProductId_whenGet_thenReturnsProduct() {
        Product product = Product.create(1L, "셔츠", "설명", 29_000L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Product result = productReader.get(1L);

        assertThat(result).isSameAs(product);
    }

    @Test
    @DisplayName("존재하지 않는 productId 로 조회하면 NOT_FOUND 예외가 발생한다")
    void givenNonExistingProductId_whenGet_thenThrowsNotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productReader.get(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("존재하는 productId 로 stock 을 조회하면 해당 stock 을 반환한다")
    void givenExistingProductId_whenGetStock_thenReturnsStock() {
        ProductStock stock = ProductStock.create(1L, 50);
        when(productStockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

        ProductStock result = productReader.getStock(1L);

        assertThat(result).isSameAs(stock);
    }

    @Test
    @DisplayName("존재하지 않는 productId 로 stock 조회 시 NOT_FOUND 예외가 발생한다")
    void givenNonExistingProductId_whenGetStock_thenThrowsNotFound() {
        when(productStockRepository.findByProductId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productReader.getStock(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }
}
