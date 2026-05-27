package com.loopers.product.application;

import com.loopers.brand.domain.BrandRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private static final Long BRAND_ID = 1L;

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductStockRepository productStockRepository = mock(ProductStockRepository.class);
    private final BrandRepository brandRepository = mock(BrandRepository.class);
    private final ProductReader productReader = mock(ProductReader.class);
    private final ProductService productService =
            new ProductService(productRepository, productStockRepository, brandRepository, productReader);

    private ProductCommand.Create createCommand() {
        return new ProductCommand.Create(BRAND_ID, "셔츠", "설명", 29_000L, 50);
    }

    @Test
    @DisplayName("create 시 Brand 가 존재하면 Product 와 Stock 을 함께 저장한다")
    void givenExistingBrand_whenCreate_thenSavesProductAndStock() {
        when(brandRepository.existsById(BRAND_ID)).thenReturn(true);
        Product saved = Product.create(BRAND_ID, "셔츠", "설명", 29_000L);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        productService.create(createCommand());

        verify(productRepository).save(any(Product.class));
        ArgumentCaptor<ProductStock> stockCaptor = ArgumentCaptor.forClass(ProductStock.class);
        verify(productStockRepository).save(stockCaptor.capture());
        assertAll(
                () -> assertThat(stockCaptor.getValue().getQuantity()).isEqualTo(50),
                () -> assertThat(stockCaptor.getValue().getProductId()).isEqualTo(saved.getId())
        );
    }

    @Test
    @DisplayName("create 시 Brand 가 존재하지 않으면 NOT_FOUND 예외가 발생하고 아무것도 저장하지 않는다")
    void givenNonExistingBrand_whenCreate_thenThrowsNotFoundAndSavesNothing() {
        when(brandRepository.existsById(BRAND_ID)).thenReturn(false);

        assertThatThrownBy(() -> productService.create(createCommand()))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);

        verify(productRepository, never()).save(any());
        verify(productStockRepository, never()).save(any());
    }

    @Test
    @DisplayName("update 커맨드로 기존 상품의 이름과 설명과 가격이 변경된다")
    void givenUpdateCommand_whenUpdate_thenChangesProductFields() {
        Product product = Product.create(BRAND_ID, "원래이름", "원래설명", 10_000L);
        when(productReader.get(1L)).thenReturn(product);

        productService.update(new ProductCommand.Update(1L, "새이름", "새설명", 20_000L));

        assertAll(
                () -> assertThat(product.getName()).isEqualTo("새이름"),
                () -> assertThat(product.getDescription()).isEqualTo("새설명"),
                () -> assertThat(product.getPrice()).isEqualTo(20_000L)
        );
    }

    @Test
    @DisplayName("update 시 존재하지 않는 productId 이면 reader 의 NOT_FOUND 예외가 전파된다")
    void givenNonExistingId_whenUpdate_thenPropagatesNotFound() {
        when(productReader.get(999L)).thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        assertThatThrownBy(() -> productService.update(new ProductCommand.Update(999L, "이름", "설명", 1000L)))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("delete 시 Product 와 Stock 의 deletedAt 이 모두 채워진다")
    void givenExistingProduct_whenDelete_thenSoftDeletesBoth() {
        Product product = Product.create(BRAND_ID, "셔츠", "설명", 29_000L);
        ProductStock stock = ProductStock.create(1L, 50);
        when(productReader.get(1L)).thenReturn(product);
        when(productReader.getStock(1L)).thenReturn(stock);

        productService.delete(1L);

        assertAll(
                () -> assertThat(product.getDeletedAt()).isNotNull(),
                () -> assertThat(stock.getDeletedAt()).isNotNull()
        );
    }

    @Test
    @DisplayName("delete 시 존재하지 않는 productId 이면 reader 의 NOT_FOUND 예외가 전파된다")
    void givenNonExistingId_whenDelete_thenPropagatesNotFound() {
        when(productReader.get(999L)).thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        assertThatThrownBy(() -> productService.delete(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("get 은 reader 의 결과를 그대로 반환한다")
    void givenExistingProductId_whenGet_thenReturnsProduct() {
        Product product = Product.create(BRAND_ID, "셔츠", "설명", 29_000L);
        when(productReader.get(1L)).thenReturn(product);

        Product result = productService.get(1L);

        assertThat(result).isSameAs(product);
    }

    @Test
    @DisplayName("getAll(LATEST) 은 repository 의 findAllOrderByLatest 결과를 반환한다")
    void givenLatestOption_whenGetAll_thenReturnsLatestOrdered() {
        Product a = Product.create(BRAND_ID, "A", "설명", 1000L);
        Product b = Product.create(BRAND_ID, "B", "설명", 2000L);
        when(productRepository.findAllOrderByLatest()).thenReturn(List.of(b, a));

        List<Product> result = productService.getAll(ProductSortOption.LATEST);

        assertThat(result).containsExactly(b, a);
    }

    @Test
    @DisplayName("getAll(PRICE_ASC) 은 repository 의 findAllOrderByPriceAsc 결과를 반환한다")
    void givenPriceAscOption_whenGetAll_thenReturnsPriceAscOrdered() {
        Product cheap = Product.create(BRAND_ID, "싼것", "설명", 1000L);
        Product expensive = Product.create(BRAND_ID, "비싼것", "설명", 9000L);
        when(productRepository.findAllOrderByPriceAsc()).thenReturn(List.of(cheap, expensive));

        List<Product> result = productService.getAll(ProductSortOption.PRICE_ASC);

        assertThat(result).containsExactly(cheap, expensive);
    }
}
