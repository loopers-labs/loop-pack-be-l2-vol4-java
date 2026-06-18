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
    @DisplayName("釉뚮옖????젣 ?붿껌 ??釉뚮옖???쒕퉬?ㅼ? ?곹뭹 ?쒕퉬?ㅼ쓽 ??젣 濡쒖쭅??紐⑤몢 ?몄텧?쒕떎.")
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
