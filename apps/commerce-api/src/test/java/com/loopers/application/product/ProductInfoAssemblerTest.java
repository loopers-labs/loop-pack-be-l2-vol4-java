package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductInfoAssemblerTest {

    private ProductInfoAssembler assembler;
    private BrandService brandService;
    private StockService stockService;
    private ProductStatsService productStatsService;

    @BeforeEach
    void setUp() {
        brandService = mock(BrandService.class);
        stockService = mock(StockService.class);
        productStatsService = mock(ProductStatsService.class);
        assembler = new ProductInfoAssembler(brandService, stockService, productStatsService);
    }

    // 비영속 ProductModel 은 id 가 null 이므로 map 키도 null 로 구성한다.
    private Map<Long, StockModel> stockMapWithNullKey(Long quantity) {
        Map<Long, StockModel> map = new HashMap<>();
        map.put(null, new StockModel(null, quantity));
        return map;
    }

    private Map<Long, ProductStatsModel> statsMapWithNullKey(ProductStatsModel stats) {
        Map<Long, ProductStatsModel> map = new HashMap<>();
        map.put(null, stats);
        return map;
    }

    private Set<Long> nullIdSet() {
        Set<Long> set = new HashSet<>();
        set.add(null);
        return set;
    }

    @DisplayName("상품 목록을 ProductInfo 목록으로 변환할 때, ")
    @Nested
    class ToInfoList {

        @DisplayName("단일 상품의 브랜드 정보와 재고 여부를 조합하여 ProductInfo 목록을 반환한다.")
        @Test
        void returnsProductInfoList_whenSingleProductExists() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", BigDecimal.valueOf(150000));
            BrandModel brand = new BrandModel("Nike");
            ProductStatsModel stats = new ProductStatsModel(product);
            when(brandService.getMapByIds(Set.of(1L))).thenReturn(Map.of(1L, brand));
            when(stockService.getMapByProductIds(nullIdSet())).thenReturn(stockMapWithNullKey(5L));
            when(productStatsService.getMapByProductIds(nullIdSet())).thenReturn(statsMapWithNullKey(stats));

            // when
            List<ProductInfo> result = assembler.toInfoList(List.of(product));

            // then
            assertAll(
                    () -> assertThat(result).hasSize(1),
                    () -> assertThat(result.get(0).brandName()).isEqualTo("Nike"),
                    () -> assertThat(result.get(0).name()).isEqualTo("에어맥스"),
                    () -> assertThat(result.get(0).price()).isEqualByComparingTo(BigDecimal.valueOf(150000)),
                    () -> assertThat(result.get(0).likeCount()).isEqualTo(0L),
                    () -> assertThat(result.get(0).inStock()).isTrue()
            );
        }

        @DisplayName("여러 브랜드에 속한 상품 목록을 각각의 브랜드 정보와 조합하여 반환한다.")
        @Test
        void returnsProductInfoList_whenMultipleBrandsExist() {
            // given
            ProductModel nikeProduct = new ProductModel(1L, "에어맥스", BigDecimal.valueOf(150000));
            ProductModel adidasProduct = new ProductModel(2L, "울트라부스트", BigDecimal.valueOf(180000));
            BrandModel nike = new BrandModel("Nike");
            BrandModel adidas = new BrandModel("Adidas");
            ProductStatsModel stats = new ProductStatsModel(nikeProduct);
            when(brandService.getMapByIds(Set.of(1L, 2L))).thenReturn(Map.of(1L, nike, 2L, adidas));
            when(stockService.getMapByProductIds(nullIdSet())).thenReturn(stockMapWithNullKey(5L));
            when(productStatsService.getMapByProductIds(nullIdSet())).thenReturn(statsMapWithNullKey(stats));

            // when
            List<ProductInfo> result = assembler.toInfoList(List.of(nikeProduct, adidasProduct));

            // then
            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> assertThat(result).anyMatch(info -> info.brandName().equals("Nike") && info.name().equals("에어맥스")),
                    () -> assertThat(result).anyMatch(info -> info.brandName().equals("Adidas") && info.name().equals("울트라부스트"))
            );
        }

        @DisplayName("빈 상품 목록이면 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyList_whenProductsIsEmpty() {
            // given
            when(brandService.getMapByIds(Set.of())).thenReturn(Map.of());
            when(stockService.getMapByProductIds(Set.of())).thenReturn(Map.of());
            when(productStatsService.getMapByProductIds(Set.of())).thenReturn(Map.of());

            // when
            List<ProductInfo> result = assembler.toInfoList(List.of());

            // then
            assertThat(result).isEmpty();
        }
    }
}
