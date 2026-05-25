package com.loopers.application.product;

import com.loopers.application.brand.BrandFinder;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.SortType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductFacadeTest {

    private ProductFacade productFacade;
    private BrandFinder brandFinder;
    private ProductFinder productFinder;

    @BeforeEach
    void setUp() {
        brandFinder = mock(BrandFinder.class);
        ProductService productService = mock(ProductService.class);
        productFinder = mock(ProductFinder.class);
        productFacade = new ProductFacade(brandFinder, productService, productFinder);
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetAllProducts {

        @DisplayName("정렬 기준별로 브랜드 정보가 포함된 상품 목록이 반환된다.")
        @ParameterizedTest
        @EnumSource(SortType.class)
        void returnsProductInfoList_withBrandInfo(SortType sortType) {
            // given
            Long brandId = 0L;
            List<ProductModel> products = List.of(
                    new ProductModel(brandId, "상품A", BigDecimal.valueOf(10000), 5L),
                    new ProductModel(brandId, "상품B", BigDecimal.valueOf(20000), 3L)
            );
            when(productFinder.getAll(sortType)).thenReturn(products);
            when(brandFinder.getMapByIds(Set.of(brandId))).thenReturn(Map.of(brandId, new BrandModel("Nike")));

            // when
            List<ProductInfo> result = productFacade.getAllProducts(sortType);

            // then
            assertThat(result).hasSize(2);
        }

        @DisplayName("상품이 없으면 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoProductsExist() {
            // given
            when(productFinder.getAll(SortType.LATEST)).thenReturn(List.of());

            // when
            List<ProductInfo> result = productFacade.getAllProducts(SortType.LATEST);

            // then
            assertThat(result).isEmpty();
        }
    }
}
