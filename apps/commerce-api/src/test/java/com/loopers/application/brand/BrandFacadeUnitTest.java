package com.loopers.application.brand;

import com.loopers.application.brand.BrandService;
import com.loopers.application.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class BrandFacadeUnitTest {

    @Mock
    private BrandService brandService;

    @Mock
    private ProductService productService;

    private BrandFacade brandFacade;

    @BeforeEach
    void setUp() {
        brandFacade = new BrandFacade(brandService, productService);
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class DeleteBrand {

        @DisplayName("브랜드를 삭제하면 소속 상품도 삭제된다.")
        @Test
        void deletesBrandAndCascadesToProducts() {
            brandFacade.deleteBrand(1L);

            then(productService).should().deleteAllByBrandId(1L);
            then(brandService).should().deleteBrand(1L);
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            doThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."))
                .when(brandService).deleteBrand(9999L);

            CoreException ex = assertThrows(CoreException.class,
                () -> brandFacade.deleteBrand(9999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
