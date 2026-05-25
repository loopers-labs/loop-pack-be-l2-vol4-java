package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
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

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 커맨드를 주면, 브랜드가 생성된다.")
        @Test
        void createsBrand_whenValidCommandIsProvided() {
            Brand brand = new Brand(1L, "무신사", "무신사 브랜드",
                ZonedDateTime.now(), ZonedDateTime.now(), null);
            given(brandService.createBrand("무신사", "무신사 브랜드")).willReturn(brand);

            BrandInfo result = brandFacade.createBrand(new BrandCommand.Create("무신사", "무신사 브랜드"));

            assertAll(
                () -> assertThat(result.id()).isEqualTo(1L),
                () -> assertThat(result.name()).isEqualTo("무신사")
            );
        }
    }

    @DisplayName("브랜드를 수정할 때,")
    @Nested
    class UpdateBrand {

        @DisplayName("존재하는 브랜드를 수정하면, 수정된 정보가 반환된다.")
        @Test
        void updatesBrand_whenBrandExists() {
            Brand updated = new Brand(1L, "새 이름", "새 설명",
                ZonedDateTime.now(), ZonedDateTime.now(), null);
            given(brandService.updateBrand(1L, "새 이름", "새 설명")).willReturn(updated);

            BrandInfo result = brandFacade.updateBrand(new BrandCommand.Update(1L, "새 이름", "새 설명"));

            assertThat(result.name()).isEqualTo("새 이름");
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            given(brandService.updateBrand(9999L, "이름", "설명"))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));

            CoreException ex = assertThrows(CoreException.class,
                () -> brandFacade.updateBrand(new BrandCommand.Update(9999L, "이름", "설명")));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
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
