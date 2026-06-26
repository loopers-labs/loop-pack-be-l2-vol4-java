package com.loopers.product.application;

import com.loopers.product.application.cache.ProductCacheStore;
import com.loopers.product.domain.ProductSortOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductReadCacheServiceTest {

    private final ProductCacheStore cacheStore = mock(ProductCacheStore.class);
    private final ProductQueryService productQueryService = mock(ProductQueryService.class);
    private final ProductReadCacheService service = new ProductReadCacheService(cacheStore, productQueryService);

    private static ProductResult.Detail detail(Long id) {
        return new ProductResult.Detail(id, 1L, "브랜드", "상품", "설명", 1000L, null, null, 5L);
    }

    private static ProductResult.Page page() {
        return new ProductResult.Page(List.of(detail(1L)), 1L, 0, 20);
    }

    @Test
    @DisplayName("getProduct: 캐시 미스면 DB 조회 후 캐시에 저장한다")
    void givenCacheMiss_whenGetProduct_thenLoadsFromDbAndCaches() {
        when(cacheStore.getDetail(1L)).thenReturn(Optional.empty());
        when(productQueryService.getProduct(1L)).thenReturn(detail(1L));

        ProductResult.Detail result = service.getProduct(1L);

        assertThat(result.id()).isEqualTo(1L);
        verify(productQueryService).getProduct(1L);
        verify(cacheStore).putDetail(1L, result);
    }

    @Test
    @DisplayName("getProduct: 캐시 적중이면 DB 를 조회하지 않는다")
    void givenCacheHit_whenGetProduct_thenSkipsDb() {
        when(cacheStore.getDetail(1L)).thenReturn(Optional.of(detail(1L)));

        ProductResult.Detail result = service.getProduct(1L);

        assertThat(result.id()).isEqualTo(1L);
        verify(productQueryService, never()).getProduct(any());
        verify(cacheStore, never()).putDetail(any(), any());
    }

    @Test
    @DisplayName("getProducts(page=0): 캐시 미스면 DB 조회 후 첫 페이지를 캐시에 저장한다")
    void givenFirstPageCacheMiss_whenGetProducts_thenLoadsAndCaches() {
        ProductCommand.PageQuery query = new ProductCommand.PageQuery(null, ProductSortOption.LIKES_DESC, 0, 20);
        when(cacheStore.getFirstPage(query)).thenReturn(Optional.empty());
        when(productQueryService.getProducts(query)).thenReturn(page());

        ProductResult.Page result = service.getProducts(query);

        assertThat(result.content()).hasSize(1);
        verify(productQueryService).getProducts(query);
        verify(cacheStore).putFirstPage(query, result);
    }

    @Test
    @DisplayName("getProducts(page=0): 캐시 적중이면 DB 를 조회하지 않는다")
    void givenFirstPageCacheHit_whenGetProducts_thenSkipsDb() {
        ProductCommand.PageQuery query = new ProductCommand.PageQuery(null, ProductSortOption.LIKES_DESC, 0, 20);
        when(cacheStore.getFirstPage(query)).thenReturn(Optional.of(page()));

        service.getProducts(query);

        verify(productQueryService, never()).getProducts(any());
        verify(cacheStore, never()).putFirstPage(any(), any());
    }

    @Test
    @DisplayName("getProducts(page!=0): 첫 페이지가 아니면 캐시를 거치지 않고 DB 로만 조회한다")
    void givenNonFirstPage_whenGetProducts_thenBypassesCache() {
        ProductCommand.PageQuery query = new ProductCommand.PageQuery(null, ProductSortOption.LIKES_DESC, 2, 20);
        when(productQueryService.getProducts(query)).thenReturn(page());

        service.getProducts(query);

        verify(productQueryService).getProducts(query);
        verify(cacheStore, never()).getFirstPage(any());
        verify(cacheStore, never()).putFirstPage(any(), any());
    }
}
