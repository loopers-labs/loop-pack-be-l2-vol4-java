package com.loopers.application.product;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.service.BrandDomainService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductDomainService;
import com.loopers.domain.stock.service.StockDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProductApplicationServiceTest {

    private BrandDomainService brandDomainService;
    private ProductDomainService productDomainService;
    private StockDomainService stockDomainService;
    private ProductApplicationService productApplicationService;

    @BeforeEach
    void setUp() {
        brandDomainService = mock(BrandDomainService.class);
        productDomainService = mock(ProductDomainService.class);
        stockDomainService = mock(StockDomainService.class);
        productApplicationService = new ProductApplicationService(brandDomainService, productDomainService, stockDomainService);
    }

    @DisplayName("상품을 등록할 때, ")
    @Nested
    class CreateProduct {

        @DisplayName("올바른 값이 주어지면, 상품과 재고가 함께 생성된다.")
        @Test
        void createsProductAndStock_whenInputIsValid() {
            // Arrange
            Long brandId = 1L;
            Brand brand = Brand.create("나이키");
            Product product = Product.create(brandId, "에어맥스", "편안한 운동화", 100_000L);

            when(brandDomainService.getBrand(brandId)).thenReturn(brand);
            when(productDomainService.createProduct(anyLong(), anyString(), anyString(), anyLong())).thenReturn(product);

            // Act
            productApplicationService.createProduct(brandId, "에어맥스", "편안한 운동화", 100_000L, 10);

            // Assert
            verify(brandDomainService, times(1)).getBrand(brandId);
            verify(productDomainService, times(1)).createProduct(brandId, "에어맥스", "편안한 운동화", 100_000L);
            verify(stockDomainService, times(1)).createStock(product.getId(), 10);
        }

        @DisplayName("존재하지 않는 브랜드 ID이면, NOT_FOUND 예외가 발생하고 상품은 생성되지 않는다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // Arrange
            Long brandId = 99L;
            doThrow(new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."))
                .when(brandDomainService).getBrand(brandId);

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                productApplicationService.createProduct(brandId, "에어맥스", "편안한 운동화", 100_000L, 10)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(productDomainService, never()).createProduct(anyLong(), anyString(), anyString(), anyLong());
            verify(stockDomainService, never()).createStock(anyLong(), anyInt());
        }
    }
}
