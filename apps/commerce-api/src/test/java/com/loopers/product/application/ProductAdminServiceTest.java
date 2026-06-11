package com.loopers.product.application;

import com.loopers.brand.domain.BrandRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStatus;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductAdminServiceTest {

    private static final Long BRAND_ID = 1L;

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductStockRepository productStockRepository = mock(ProductStockRepository.class);
    private final BrandRepository brandRepository = mock(BrandRepository.class);
    private final ProductReader productReader = mock(ProductReader.class);
    private final ProductAdminService productAdminService =
            new ProductAdminService(productRepository, productStockRepository, brandRepository, productReader);

    private ProductCommand.Create createCommand() {
        return new ProductCommand.Create(BRAND_ID, "셔츠", "설명", 29_000L, "https://cdn/shirt.png", 50);
    }

    @Test
    @DisplayName("create 시 Brand 가 존재하면 Product 와 Stock 을 저장하고 재고 포함 AdminDetail 을 반환한다")
    void givenExistingBrand_whenCreate_thenSavesProductAndStock() {
        when(brandRepository.existsById(BRAND_ID)).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResult.AdminDetail result = productAdminService.create(createCommand());

        ArgumentCaptor<ProductStock> stockCaptor = ArgumentCaptor.forClass(ProductStock.class);
        verify(productStockRepository).save(stockCaptor.capture());
        assertAll(
                () -> assertThat(stockCaptor.getValue().getQuantity()).isEqualTo(50),
                () -> assertThat(result.status()).isEqualTo(ProductStatus.ON_SALE),
                () -> assertThat(result.thumbnailUrl()).isEqualTo("https://cdn/shirt.png"),
                () -> assertThat(result.stockQuantity()).isEqualTo(50)
        );
    }

    @Test
    @DisplayName("create 시 Brand 가 없으면 NOT_FOUND 가 발생하고 아무것도 저장하지 않는다")
    void givenNonExistingBrand_whenCreate_thenThrowsNotFoundAndSavesNothing() {
        when(brandRepository.existsById(BRAND_ID)).thenReturn(false);

        assertThatThrownBy(() -> productAdminService.create(createCommand()))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);

        verify(productRepository, never()).save(any());
        verify(productStockRepository, never()).save(any());
    }

    @Test
    @DisplayName("update 커맨드로 기존 상품의 이름·설명·가격·썸네일이 변경되고 재고 포함 AdminDetail 을 반환한다")
    void givenUpdateCommand_whenUpdate_thenChangesFields() {
        Product product = Product.create(BRAND_ID, "원래", "원래설명", 10_000L, "old.png");
        when(productReader.get(1L)).thenReturn(product);
        when(productReader.getStock(1L)).thenReturn(ProductStock.create(1L, 30));

        ProductResult.AdminDetail result =
                productAdminService.update(new ProductCommand.Update(1L, "새이름", "새설명", 20_000L, "new.png"));

        assertAll(
                () -> assertThat(product.getName()).isEqualTo("새이름"),
                () -> assertThat(product.getThumbnailUrl()).isEqualTo("new.png"),
                () -> assertThat(result.price()).isEqualTo(20_000L),
                () -> assertThat(result.stockQuantity()).isEqualTo(30)
        );
    }

    @Test
    @DisplayName("update 시 존재하지 않는 productId 이면 NOT_FOUND 가 전파된다")
    void givenNonExistingId_whenUpdate_thenPropagatesNotFound() {
        when(productReader.get(999L)).thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        assertThatThrownBy(() -> productAdminService.update(new ProductCommand.Update(999L, "이름", "설명", 1000L, null)))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("delete 시 Product 와 Stock 의 deletedAt 이 모두 채워진다")
    void givenExistingProduct_whenDelete_thenSoftDeletesBoth() {
        Product product = Product.create(BRAND_ID, "셔츠", "설명", 29_000L, null);
        ProductStock stock = ProductStock.create(1L, 50);
        when(productReader.get(1L)).thenReturn(product);
        when(productReader.getStock(1L)).thenReturn(stock);

        productAdminService.delete(1L);

        assertAll(
                () -> assertThat(product.getDeletedAt()).isNotNull(),
                () -> assertThat(stock.getDeletedAt()).isNotNull()
        );
    }

    @Test
    @DisplayName("suspend 는 상품을 SUSPENDED 로 전환한다")
    void givenProduct_whenSuspend_thenStatusSuspended() {
        Product product = Product.create(BRAND_ID, "셔츠", "설명", 29_000L, null);
        when(productReader.get(1L)).thenReturn(product);

        productAdminService.suspend(1L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.SUSPENDED);
    }

    @Test
    @DisplayName("resume 는 상품을 ON_SALE 로 전환한다")
    void givenSuspendedProduct_whenResume_thenStatusOnSale() {
        Product product = Product.create(BRAND_ID, "셔츠", "설명", 29_000L, null);
        product.suspend();
        when(productReader.get(1L)).thenReturn(product);

        productAdminService.resume(1L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("get 은 상태 무관 상품을 재고와 함께 AdminDetail 로 반환한다")
    void givenProduct_whenGet_thenReturnsAdminDetailWithStock() {
        Product product = Product.create(BRAND_ID, "셔츠", "설명", 29_000L, null);
        product.suspend();
        when(productReader.get(1L)).thenReturn(product);
        when(productReader.getStock(1L)).thenReturn(ProductStock.create(1L, 7));

        ProductResult.AdminDetail result = productAdminService.get(1L);

        assertAll(
                () -> assertThat(result.status()).isEqualTo(ProductStatus.SUSPENDED),
                () -> assertThat(result.stockQuantity()).isEqualTo(7)
        );
    }
}
