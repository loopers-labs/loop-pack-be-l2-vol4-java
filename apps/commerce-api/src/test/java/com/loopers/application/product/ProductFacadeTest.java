package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.application.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductRepository;
import com.loopers.application.like.LikeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @InjectMocks
    private ProductFacade productFacade;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private LikeRepository likeRepository;

    @Test
    @DisplayName("상품 목록을 페이지 조회하여 브랜드명을 병합한 DTO 목록을 반환한다.")
    void getProducts_ShouldReturnProductsWithBrandNames() {
        // given
        Long brandId = 10L;
        String sort = "latest";
        Pageable pageable = PageRequest.of(0, 10);

        ProductModel product1 = new ProductModel(brandId, "Air Max", new BigDecimal("1000.0000"));
        ReflectionTestUtils.setField(product1, "id", 1L);

        Page<ProductModel> productPage = new PageImpl<>(List.of(product1), pageable, 1);
        given(productRepository.findAll(brandId, sort, pageable)).willReturn(productPage);

        BrandModel brand = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand, "id", brandId);
        given(brandRepository.findByIds(List.of(brandId))).willReturn(List.of(brand));

        given(likeRepository.countByProductIds(List.of(1L))).willReturn(Map.of(1L, 5));

        // when
        Page<ProductInfo> result = productFacade.getProducts(brandId, sort, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        ProductInfo info = result.getContent().get(0);
        assertThat(info.id()).isEqualTo(1L);
        assertThat(info.brandId()).isEqualTo(brandId);
        assertThat(info.brandName()).isEqualTo("Nike");
        assertThat(info.name()).isEqualTo("Air Max");
        assertThat(info.likeCount()).isEqualTo(5);
    }
}
