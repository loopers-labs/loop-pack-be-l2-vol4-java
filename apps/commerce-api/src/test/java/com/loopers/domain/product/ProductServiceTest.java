package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
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

    @Mock
    private BrandRepository brandRepository;

    @Test
    @DisplayName("상품 목록 조회를 요청하면 필터와 정렬이 적용된 목록이 반환된다.")
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
    @DisplayName("상품 상세 조회를 요청하면 상품 정보와 브랜드 정보가 함께 반환된다.")
    void getProductDetail_ShouldReturnProductWithBrand() {
        // given
        Long productId = 1L;
        Long brandId = 10L;
        ProductModel product = new ProductModel(brandId, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId);
        
        BrandModel brand = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand, "id", brandId);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

        // when
        ProductDetail detail = productService.getProductDetail(productId);

        // then
        assertThat(detail.productId()).isEqualTo(productId);
        assertThat(detail.name()).isEqualTo("Air Jordan");
        assertThat(detail.brandName()).isEqualTo("Nike");
    }

    @Test
    @DisplayName("브랜드 ID를 기반으로 연관된 모든 상품을 논리 삭제한다.")
    void deleteProductsByBrand_ShouldMarkAllAsDeleted() {
        // given
        Long brandId = 1L;

        // when
        productService.deleteProductsByBrand(brandId);

        // then
        org.mockito.Mockito.verify(productRepository).deleteByBrandId(brandId);
    }
}
