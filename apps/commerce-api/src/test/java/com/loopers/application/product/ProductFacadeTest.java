package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductBrandProcessService;
import com.loopers.domain.product.ProductDetailView;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    @Mock
    private ProductBrandProcessService productBrandProcessService;

    @Mock
    private ProductCacheRepository productCacheRepository;

    @Mock
    private ProductLikeCountRepository productLikeCountRepository;

    @DisplayName("상품 목록 캐시가 있으면 브랜드 검증과 DB 조회를 건너뛴다.")
    @Test
    void returnsCachedProducts_withoutBrandValidation_whenProductListCacheExists() {
        // arrange
        ProductFacade productFacade = new ProductFacade(
            productService,
            brandService,
            productBrandProcessService,
            productCacheRepository,
            productLikeCountRepository
        );
        List<ProductInfo> cachedProducts = List.of(productInfo());
        when(productCacheRepository.getProducts(1L, "likes_desc", 0, 20))
            .thenReturn(Optional.of(cachedProducts));

        // act
        List<ProductInfo> result = productFacade.getAllProducts(1L, "likes_desc", 0, 20);

        // assert
        assertThat(result).isEqualTo(cachedProducts);
        verifyNoInteractions(brandService, productService, productBrandProcessService);
    }

    @DisplayName("상품 상세 조회 시 Redis 좋아요 카운터가 있으면 응답 좋아요 수에 반영한다.")
    @Test
    void returnsProductWithLatestLikeCount_whenRedisLikeCountExists() {
        // arrange
        ProductFacade productFacade = new ProductFacade(
            productService,
            brandService,
            productBrandProcessService,
            productCacheRepository,
            productLikeCountRepository
        );
        Product product = Product.reconstruct(1L, 10L, "니트", "부드러운 니트", 30_000L, 10, 1, false);
        Brand brand = Brand.reconstruct(10L, "Loopers", "감성 이커머스 브랜드", false);
        when(productCacheRepository.getProduct(1L)).thenReturn(Optional.empty());
        when(productService.getProduct(1L)).thenReturn(product);
        when(brandService.getBrand(10L)).thenReturn(brand);
        when(productBrandProcessService.getProductDetailView(product, brand))
            .thenReturn(new ProductDetailView(product, brand));
        when(productLikeCountRepository.get(1L)).thenReturn(Optional.of(7));

        // act
        ProductInfo result = productFacade.getProduct(1L);

        // assert
        assertThat(result.likeCount()).isEqualTo(7);
    }

    private ProductInfo productInfo() {
        return new ProductInfo(
            1L,
            new ProductInfo.BrandInfo(1L, "Loopers", "감성 이커머스 브랜드"),
            "니트",
            "부드러운 니트",
            30_000L,
            10,
            5
        );
    }
}
