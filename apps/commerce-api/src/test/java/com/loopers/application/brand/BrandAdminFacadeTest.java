package com.loopers.application.brand;

import com.loopers.domain.brand.BrandAdminService;
import com.loopers.domain.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandAdminFacadeTest {

    @InjectMocks
    private BrandAdminFacade brandAdminFacade;

    @Mock
    private BrandAdminService brandAdminService;

    @Mock
    private ProductService productService;

    @Test
    @DisplayName("브랜드 삭제 요청 시 브랜드 서비스와 상품 서비스의 삭제 로직이 모두 호출된다.")
    void deleteBrand_ShouldCallBothServices() {
        // given
        Long brandId = 1L;

        // when
        brandAdminFacade.deleteBrand(brandId);

        // then
        verify(brandAdminService).deleteBrand(brandId);
        verify(productService).deleteProductsByBrand(brandId);
    }
}
