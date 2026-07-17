package com.loopers.application.product;

import com.loopers.domain.product.ProductLikeViewRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.stock.StockModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @Mock
    private ProductService productService;

    @Mock
    private com.loopers.application.stock.StockService stockService;

    @Mock
    private com.loopers.application.brand.BrandService brandService;

    @Mock
    private com.loopers.domain.product.ProductDomainService productDomainService;

    @Mock
    private ProductLikeViewRepository productLikeViewRepository;

    @Mock
    private ProductCacheService productCacheService;

    @Mock
    private RankingRepository rankingRepository;

    @InjectMocks
    private ProductFacade productFacade;

    @DisplayName("랭킹 조회(Redis) 중 예외가 발생해도 상품 상세 조회는 실패하지 않고 rank는 null로 처리된다.")
    @Test
    void getProduct_degradesRankToNull_whenRankingRepositoryThrows() {
        Long productId = 1L;

        when(rankingRepository.getRank(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(productId)))
            .thenThrow(new RuntimeException("Redis connection failed"));

        when(productCacheService.getDetail(productId))
            .thenReturn(Optional.of(new ProductCacheItem(productId, "상품A", 10L, "브랜드A", 5)));

        ProductModel product = mock(ProductModel.class);
        when(product.getPrice()).thenReturn(10000L);
        when(productService.getById(productId)).thenReturn(product);

        StockModel stock = mock(StockModel.class);
        when(stock.getQuantity()).thenReturn(3);
        when(stockService.getByProductId(productId)).thenReturn(stock);

        ProductInfo result = productFacade.getProduct(productId);

        assertThat(result.rank()).isNull();
        assertThat(result.id()).isEqualTo(productId);
        assertThat(result.name()).isEqualTo("상품A");
        assertThat(result.price()).isEqualTo(10000L);
        assertThat(result.stockQuantity()).isEqualTo(3);
    }
}
