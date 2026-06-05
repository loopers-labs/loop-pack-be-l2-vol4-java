package com.loopers.product.application;

import com.loopers.brand.application.BrandReader;
import com.loopers.brand.domain.Brand;
import com.loopers.like.application.LikeReader;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductDisplayStatus;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductQueryServiceTest {

    private static final Long BRAND_ID = 1L;

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductStockRepository productStockRepository = mock(ProductStockRepository.class);
    private final ProductReader productReader = mock(ProductReader.class);
    private final BrandReader brandReader = mock(BrandReader.class);
    private final LikeReader likeReader = mock(LikeReader.class);
    private final ProductQueryService productQueryService =
            new ProductQueryService(productReader, brandReader, likeReader, productRepository, productStockRepository);

    @Test
    @DisplayName("get 은 판매중 상품을 재고와 함께 Detail 로 매핑하고, 재고가 있으면 displayStatus=ON_SALE 이다")
    void givenOnSaleProductWithStock_whenGet_thenReturnsDetailWithOnSale() {
        Product product = Product.create(BRAND_ID, "셔츠", "설명", 29_000L, "https://cdn/loopers.png");
        when(productReader.getActive(1L)).thenReturn(product);
        when(productReader.getStock(1L)).thenReturn(ProductStock.create(1L, 10));
        when(brandReader.get(BRAND_ID)).thenReturn(Brand.create("브랜드", null, null));
        when(likeReader.countActive(1L)).thenReturn(0L);

        ProductResult.Detail result = productQueryService.get(1L);

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
        when(productReader.getActive(1L)).thenReturn(product);
        when(productReader.getStock(1L)).thenReturn(ProductStock.create(1L, 0));
        when(brandReader.get(BRAND_ID)).thenReturn(Brand.create("브랜드", null, null));
        when(likeReader.countActive(1L)).thenReturn(0L);

        ProductResult.Detail result = productQueryService.get(1L);

        assertThat(result.displayStatus()).isEqualTo(ProductDisplayStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("get 은 브랜드명과 활성 좋아요 수를 함께 조합해 반환한다")
    void givenProduct_whenGet_thenIncludesBrandNameAndLikeCount() {
        Product product = Product.create(BRAND_ID, "셔츠", "설명", 29_000L, null);
        when(productReader.getActive(1L)).thenReturn(product);
        when(productReader.getStock(1L)).thenReturn(ProductStock.create(1L, 10));
        when(brandReader.get(BRAND_ID)).thenReturn(Brand.create("나이키", null, null));
        when(likeReader.countActive(1L)).thenReturn(5L);

        ProductResult.Detail result = productQueryService.get(1L);

        assertAll(
                () -> assertThat(result.brandName()).isEqualTo("나이키"),
                () -> assertThat(result.likeCount()).isEqualTo(5L)
        );
    }

    @Test
    @DisplayName("get 은 판매중지·삭제 상품(getActive 미존재)이면 NOT_FOUND 가 전파된다")
    void givenNonActiveProduct_whenGet_thenPropagatesNotFound() {
        when(productReader.getActive(999L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        assertThatThrownBy(() -> productQueryService.get(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("getAll(LATEST) 은 판매중 상품을 latest 정렬로 매핑한다")
    void givenLatestOption_whenGetAll_thenReturnsDetailsInLatestOrder() {
        Product a = Product.create(BRAND_ID, "A", "설명", 1000L, null);
        Product b = Product.create(BRAND_ID, "B", "설명", 2000L, null);
        when(productRepository.findAllOnSaleOrderByLatest()).thenReturn(List.of(b, a));
        when(productStockRepository.findAllByProductIdIn(anyList())).thenReturn(List.of());
        when(brandReader.getNames(anyList())).thenReturn(Map.of());
        when(likeReader.countActiveByProductIds(anyList())).thenReturn(Map.of());

        List<ProductResult.Detail> result = productQueryService.getAll(ProductSortOption.LATEST);

        assertThat(result)
                .extracting(ProductResult.Detail::name)
                .containsExactly("B", "A");
    }

    @Test
    @DisplayName("getAll(PRICE_ASC) 은 판매중 상품을 price asc 정렬로 매핑한다")
    void givenPriceAscOption_whenGetAll_thenReturnsDetailsInPriceAscOrder() {
        Product cheap = Product.create(BRAND_ID, "싼것", "설명", 1000L, null);
        Product expensive = Product.create(BRAND_ID, "비싼것", "설명", 9000L, null);
        when(productRepository.findAllOnSaleOrderByPriceAsc()).thenReturn(List.of(cheap, expensive));
        when(productStockRepository.findAllByProductIdIn(anyList())).thenReturn(List.of());
        when(brandReader.getNames(anyList())).thenReturn(Map.of());
        when(likeReader.countActiveByProductIds(anyList())).thenReturn(Map.of());

        List<ProductResult.Detail> result = productQueryService.getAll(ProductSortOption.PRICE_ASC);

        assertThat(result)
                .extracting(ProductResult.Detail::name)
                .containsExactly("싼것", "비싼것");
    }

    @Test
    @DisplayName("getAll(LIKES_DESC) 은 likeCount 정렬 결과를 그대로 매핑한다")
    void givenLikesDescOption_whenGetAll_thenReturnsDetailsInRepositoryOrder() {
        Product popular = Product.create(BRAND_ID, "인기", "설명", 1000L, null);
        Product unpopular = Product.create(BRAND_ID, "비인기", "설명", 2000L, null);
        when(productRepository.findAllOnSaleOrderByLikeCountDesc()).thenReturn(List.of(popular, unpopular));
        when(productStockRepository.findAllByProductIdIn(anyList())).thenReturn(List.of());
        when(brandReader.getNames(anyList())).thenReturn(Map.of());
        when(likeReader.countActiveByProductIds(anyList())).thenReturn(Map.of());

        List<ProductResult.Detail> result = productQueryService.getAll(ProductSortOption.LIKES_DESC);

        assertThat(result)
                .extracting(ProductResult.Detail::name)
                .containsExactly("인기", "비인기");
    }
}
