package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @InjectMocks
    private StockService stockService;

    @Mock
    private ProductRepository productRepository;

    @Test
    @DisplayName("재고 차감 요청 시 수량이 충분하면 차감된다.")
    void decreaseStocks_Success() {
        // given
        Long productId = 1L;
        int initialQuantity = 10;
        int decreaseQuantity = 3;
        
        ProductModel product = new ProductModel(10L, "Air Jordan", new BigDecimal("200000"));
        product.assignStock(initialQuantity);
        
        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when
        stockService.decreaseStocks(List.of(new StockService.StockRequest(productId, decreaseQuantity)));

        // then
        assertThat(product.getStock().getQuantity()).isEqualTo(initialQuantity - decreaseQuantity);
    }

    @Test
    @DisplayName("재고가 부족하면 예외가 발생한다.")
    void decreaseStocks_Insufficient_ShouldThrowException() {
        // given
        Long productId = 1L;
        int initialQuantity = 2;
        int decreaseQuantity = 5;
        
        ProductModel product = new ProductModel(10L, "Air Jordan", new BigDecimal("200000"));
        product.assignStock(initialQuantity);
        
        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        // when & then
        assertThrows(CoreException.class, () -> 
                stockService.decreaseStocks(List.of(new StockService.StockRequest(productId, decreaseQuantity)))
        );
    }
}
