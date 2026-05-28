package com.loopers.application.brand;

import com.loopers.domain.brand.model.Brand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BrandFacadeTest {

    private BrandApplicationService brandApplicationService;
    private BrandFacade brandFacade;

    @BeforeEach
    void setUp() {
        brandApplicationService = mock(BrandApplicationService.class);
        brandFacade = new BrandFacade(brandApplicationService);
    }

    @DisplayName("브랜드 목록을 조회할 때, ")
    @Nested
    class GetBrands {

        @DisplayName("페이징 요청이 주어지면, BrandInfo 목록을 반환한다.")
        @Test
        void returnsBrandInfoPage_whenPageableIsGiven() {
            // Arrange
            Brand brand = Brand.create("나이키");
            PageRequest pageable = PageRequest.of(0, 20);
            when(brandApplicationService.getBrands(pageable)).thenReturn(new PageImpl<>(List.of(brand)));

            // Act
            Page<BrandInfo> result = brandFacade.getBrands(0, 20);

            // Assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("나이키");
        }
    }

    @DisplayName("브랜드를 조회할 때, ")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 ID이면, BrandInfo를 반환한다.")
        @Test
        void returnsBrandInfo_whenIdExists() {
            // Arrange
            Brand brand = Brand.create("나이키");
            when(brandApplicationService.getBrand(1L)).thenReturn(brand);

            // Act
            BrandInfo result = brandFacade.getBrand(1L);

            // Assert
            assertThat(result.name()).isEqualTo("나이키");
        }

        @DisplayName("존재하지 않는 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            // Arrange
            when(brandApplicationService.getBrand(999L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                brandFacade.getBrand(999L)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
