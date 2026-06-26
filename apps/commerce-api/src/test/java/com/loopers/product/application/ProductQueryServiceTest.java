package com.loopers.product.application;

import com.loopers.brand.application.BrandReader;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductDisplayStatus;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductSortOption;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.product.domain.ProductErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductQueryServiceTest {

    private static final Long BRAND_ID = 1L;

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductStockRepository productStockRepository = mock(ProductStockRepository.class);
    private final BrandReader brandReader = mock(BrandReader.class);
    private final ProductQueryService productQueryService =
            new ProductQueryService(brandReader, productRepository, productStockRepository);

    @Test
    @DisplayName("get 은 판매중 상품을 재고와 함께 Detail 로 매핑하고, 재고가 있으면 displayStatus=ON_SALE 이다")
    void givenOnSaleProductWithStock_whenGet_thenReturnsDetailWithOnSale() {
        Product product = Product.create(BRAND_ID, "셔츠", "설명", 29_000L, "https://cdn/loopers.png");
        when(productRepository.findActiveById(1L)).thenReturn(Optional.of(product));
        when(productStockRepository.findByProductId(1L)).thenReturn(Optional.of(ProductStock.create(1L, 10)));
        when(brandReader.getName(BRAND_ID)).thenReturn("브랜드");

        ProductResult.Detail result = productQueryService.getProduct(1L);

        assertAll(
                () -> assertThat(result.name()).isEqualTo("셔츠"),
                () -> assertThat(result.price()).isEqualTo(29_000L),
                () -> assertThat(result.displayStatus()).isEqualTo(ProductDisplayStatus.ON_SALE)
        );
    }

    @Test
    @DisplayName("get 은 재고가 0 이면 displayStatus=SOLD_OUT 으로 계산한다")
    void givenOnSaleProductWithZeroStock_whenGet_thenDisplayStatusSoldOut() {
        Product product = Product.create(BRAND_ID, "셔츠", "설명", 29_000L, null);
        when(productRepository.findActiveById(1L)).thenReturn(Optional.of(product));
        when(productStockRepository.findByProductId(1L)).thenReturn(Optional.of(ProductStock.create(1L, 0)));
        when(brandReader.getName(BRAND_ID)).thenReturn("브랜드");

        ProductResult.Detail result = productQueryService.getProduct(1L);

        assertThat(result.displayStatus()).isEqualTo(ProductDisplayStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("get 은 브랜드명과 비정규화된 좋아요 수(like_count)를 함께 조합해 반환한다")
    void givenProduct_whenGet_thenIncludesBrandNameAndLikeCount() {
        Product product = Product.create(BRAND_ID, "셔츠", "설명", 29_000L, null);
        ReflectionTestUtils.setField(product, "likeCount", 5L);
        when(productRepository.findActiveById(1L)).thenReturn(Optional.of(product));
        when(productStockRepository.findByProductId(1L)).thenReturn(Optional.of(ProductStock.create(1L, 10)));
        when(brandReader.getName(BRAND_ID)).thenReturn("나이키");

        ProductResult.Detail result = productQueryService.getProduct(1L);

        assertAll(
                () -> assertThat(result.brandName()).isEqualTo("나이키"),
                () -> assertThat(result.likeCount()).isEqualTo(5L)
        );
    }

    @Test
    @DisplayName("get 은 판매중지·삭제 상품(getActive 미존재)이면 NOT_FOUND 가 전파된다")
    void givenNonActiveProduct_whenGet_thenPropagatesNotFound() {
        when(productRepository.findActiveById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productQueryService.getProduct(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("getProducts 는 데이터 결함으로 브랜드·재고 행이 깨져 있어도 목록 전체를 실패시키지 않는다(정상 로직에선 생성·삭제가 원자적이라 발생 불가)")
    void givenCorruptedAuxiliaryData_whenGetProducts_thenContentSurvivesWithDefaults() {
        Product a = Product.create(1L, "A", "설명", 1000L, null);
        Product b = Product.create(2L, "B", "설명", 2000L, null);
        ProductCommand.PageQuery query = new ProductCommand.PageQuery(null, ProductSortOption.LATEST, 0, 20);
        when(productRepository.findAllOnSale(null, ProductSortOption.LATEST, 0L, 20)).thenReturn(List.of(a, b));
        when(productRepository.countOnSale(null)).thenReturn(2L);
        when(productStockRepository.findAllByProductIdIn(anyList())).thenReturn(List.of());
        when(brandReader.getNames(anyList())).thenReturn(Map.of(1L, "브랜드A")); // brandId=2 누락

        List<ProductResult.Detail> result = productQueryService.getProducts(query).content();

        assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result.get(0).brandName()).isEqualTo("브랜드A"),
                () -> assertThat(result.get(1).brandName()).isNull(),
                () -> assertThat(result).allSatisfy(detail ->
                        assertThat(detail.displayStatus()).isEqualTo(ProductDisplayStatus.SOLD_OUT))
        );
    }

    @Test
    @DisplayName("getProducts(PageQuery) 는 brandId 로 조회해 content 와 totalCount 를 함께 담은 Page 를 반환한다")
    void givenPageQuery_whenGetProducts_thenReturnsPageWithContentAndTotalCount() {
        Product a = Product.create(BRAND_ID, "A", "설명", 1000L, null);
        Product b = Product.create(BRAND_ID, "B", "설명", 2000L, null);
        ProductCommand.PageQuery query = new ProductCommand.PageQuery(BRAND_ID, ProductSortOption.LATEST, 0, 20);
        when(productRepository.findAllOnSale(BRAND_ID, ProductSortOption.LATEST, 0L, 20)).thenReturn(List.of(a, b));
        when(productRepository.countOnSale(BRAND_ID)).thenReturn(42L);
        when(productStockRepository.findAllByProductIdIn(anyList())).thenReturn(List.of());
        when(brandReader.getNames(anyList())).thenReturn(Map.of());

        ProductResult.Page result = productQueryService.getProducts(query);

        assertAll(
                () -> assertThat(result.content())
                        .extracting(ProductResult.Detail::name)
                        .containsExactly("A", "B"),
                () -> assertThat(result.totalCount()).isEqualTo(42L),
                () -> assertThat(result.page()).isEqualTo(0),
                () -> assertThat(result.size()).isEqualTo(20)
        );
    }

    @Test
    @DisplayName("getProducts 는 page/size 로 offset 을 계산해 리포지토리에 위임한다 (offset = page * size)")
    void givenSecondPage_whenGetProducts_thenComputesOffsetAsPageTimesSize() {
        ProductCommand.PageQuery query = new ProductCommand.PageQuery(null, ProductSortOption.LATEST, 2, 20);
        when(productRepository.findAllOnSale(null, ProductSortOption.LATEST, 40L, 20)).thenReturn(List.of());
        when(productRepository.countOnSale(null)).thenReturn(0L);

        ProductResult.Page result = productQueryService.getProducts(query);

        assertThat(result.content()).isEmpty();
        verify(productRepository).findAllOnSale(null, ProductSortOption.LATEST, 40L, 20);
    }
}
