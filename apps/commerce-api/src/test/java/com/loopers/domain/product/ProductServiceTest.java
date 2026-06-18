package com.loopers.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Test
    @DisplayName("?곹뭹 紐⑸줉 議고쉶瑜??붿껌?섎㈃ ?꾪꽣? ?뺣젹???곸슜??紐⑸줉??諛섑솚?쒕떎.")
    void getProducts_ShouldReturnFilteredAndSortedPage() {
        // given
        Long brandId = 1L;
        String sort = "likes_desc";
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        
        given(productRepository.findAll(brandId, sort, pageable))
                .willReturn(org.springframework.data.domain.Page.empty());

        // when
        productService.getProducts(brandId, sort, pageable);

        // then
        org.mockito.Mockito.verify(productRepository).findAll(brandId, sort, pageable);
    }

    @Test
    @DisplayName("釉뚮옖??ID瑜?湲곕컲?쇰줈 ?곌???紐⑤뱺 ?곹뭹???쇰━ ??젣?쒕떎.")
    void deleteProductsByBrand_ShouldMarkAllAsDeleted() {
        // given
        Long brandId = 1L;

        // when
        productService.deleteProductsByBrand(brandId);

        // then
        org.mockito.Mockito.verify(productRepository).deleteByBrandId(brandId);
    }
}
