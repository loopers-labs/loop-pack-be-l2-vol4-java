package com.loopers.domain.product;

import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.product.service.ProductDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductDomainServiceTest {

    private ProductRepository productRepository;
    private ProductDomainService productDomainService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productDomainService = new ProductDomainService(productRepository);
    }

    @DisplayName("상품을 조회할 때, ")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 ID이면, 상품을 반환한다.")
        @Test
        void returnsProduct_whenIdExists() {
            // Arrange
            Product product = Product.create(1L, "나이키 에어맥스", "편안한 운동화", 100_000L);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            // Act
            Product result = productDomainService.getProduct(1L);

            // Assert
            assertThat(result.getName()).isEqualTo("나이키 에어맥스");
        }

        @DisplayName("존재하지 않는 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            // Arrange
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                productDomainService.getProduct(99L)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

}
