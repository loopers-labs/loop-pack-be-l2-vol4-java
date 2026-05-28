package com.loopers.application.product;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.brand.service.BrandDomainService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.product.service.ProductDomainService;
import com.loopers.domain.stock.model.Stock;
import com.loopers.domain.stock.repository.StockRepository;
import com.loopers.domain.stock.service.StockDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProductApplicationServiceTest {

    private BrandDomainService brandDomainService;
    private ProductDomainService productDomainService;
    private StockDomainService stockDomainService;
    private ProductRepository productRepository;
    private BrandRepository brandRepository;
    private StockRepository stockRepository;
    private ProductApplicationService productApplicationService;

    @BeforeEach
    void setUp() {
        brandDomainService = mock(BrandDomainService.class);
        productDomainService = mock(ProductDomainService.class);
        stockDomainService = mock(StockDomainService.class);
        productRepository = mock(ProductRepository.class);
        brandRepository = mock(BrandRepository.class);
        stockRepository = mock(StockRepository.class);
        productApplicationService = new ProductApplicationService(
            brandDomainService, productDomainService, stockDomainService,
            productRepository, brandRepository, stockRepository
        );
    }

    @DisplayName("상품 단건을 조회할 때, ")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품이면, 브랜드명과 재고 여부가 포함된 ProductInfo를 반환한다.")
        @Test
        void returnsProductInfo_whenProductExists() {
            // Arrange
            Product product = Product.create(1L, "에어맥스", "운동화", 100_000L);
            Brand brand = Brand.create("나이키");
            Stock stock = Stock.create(product.getId(), 10);

            when(productDomainService.getProduct(1L)).thenReturn(product);
            when(brandDomainService.getBrand(product.getBrandId())).thenReturn(brand);
            when(stockDomainService.getStock(anyLong())).thenReturn(stock);

            // Act
            ProductInfo result = productApplicationService.getProduct(1L);

            // Assert
            assertThat(result.name()).isEqualTo("에어맥스");
            assertThat(result.inStock()).isTrue();
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // Arrange
            when(productDomainService.getProduct(999L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                productApplicationService.getProduct(999L)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    class GetProducts {

        @DisplayName("brandId 없이 요청하면, 전체 상품 목록을 브랜드명/재고 포함해 반환한다.")
        @Test
        void returnsProductInfoPage_whenBrandIdIsNull() {
            // Arrange
            Product product = Product.create(1L, "에어맥스", "운동화", 100_000L);
            Brand brand = mock(Brand.class);
            when(brand.getId()).thenReturn(1L);
            when(brand.getName()).thenReturn("나이키");
            Stock stock = mock(Stock.class);
            when(stock.getProductId()).thenReturn(product.getId());
            when(stock.getQuantity()).thenReturn(10);

            PageRequest pageable = PageRequest.of(0, 20, ProductSort.LATEST.toSort());
            when(productRepository.findAll(null, pageable)).thenReturn(new PageImpl<>(List.of(product)));
            when(brandRepository.findAllByIdIn(anyList())).thenReturn(List.of(brand));
            when(stockRepository.findAllByProductIdIn(anyList())).thenReturn(List.of(stock));

            // Act
            Page<ProductInfo> result = productApplicationService.getProducts(null, ProductSort.LATEST, 0, 20);

            // Assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("에어맥스");
            assertThat(result.getContent().get(0).brandName()).isEqualTo("나이키");
            assertThat(result.getContent().get(0).inStock()).isTrue();
        }
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
