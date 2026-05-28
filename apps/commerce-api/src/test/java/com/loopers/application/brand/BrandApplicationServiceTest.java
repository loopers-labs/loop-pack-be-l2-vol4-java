package com.loopers.application.brand;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.brand.service.BrandDomainService;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.stock.repository.StockRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class BrandApplicationServiceTest {

    private BrandDomainService brandDomainService;
    private BrandRepository brandRepository;
    private ProductRepository productRepository;
    private StockRepository stockRepository;
    private BrandApplicationService brandApplicationService;

    @BeforeEach
    void setUp() {
        brandDomainService = mock(BrandDomainService.class);
        brandRepository = mock(BrandRepository.class);
        productRepository = mock(ProductRepository.class);
        stockRepository = mock(StockRepository.class);
        brandApplicationService = new BrandApplicationService(
            brandDomainService, brandRepository, productRepository, stockRepository
        );
    }

    @DisplayName("브랜드를 조회할 때, ")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 ID이면, 브랜드를 반환한다.")
        @Test
        void returnsBrand_whenIdExists() {
            // Arrange
            Brand brand = Brand.create("나이키");
            when(brandDomainService.getBrand(1L)).thenReturn(brand);

            // Act
            Brand result = brandApplicationService.getBrand(1L);

            // Assert
            assertThat(result.getName()).isEqualTo("나이키");
        }

        @DisplayName("존재하지 않는 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            // Arrange
            when(brandDomainService.getBrand(999L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                brandApplicationService.getBrand(999L)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 목록을 조회할 때, ")
    @Nested
    class GetBrands {

        @DisplayName("페이징 요청이 주어지면, 브랜드 목록을 반환한다.")
        @Test
        void returnsBrands_whenPageableIsGiven() {
            // Arrange
            Brand brand = Brand.create("나이키");
            PageRequest pageable = PageRequest.of(0, 20);
            when(brandRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(brand)));

            // Act
            Page<Brand> result = brandApplicationService.getBrands(pageable);

            // Assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("나이키");
        }
    }

    @DisplayName("브랜드를 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("올바른 이름이 주어지면, save()가 호출된다.")
        @Test
        void callsSave_whenNameIsValid() {
            // Arrange
            String name = "나이키";
            when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            brandApplicationService.register(name);

            // Assert
            verify(brandDomainService, times(1)).validateDuplicateName(name);
            verify(brandRepository, times(1)).save(any(Brand.class));
        }

        @DisplayName("중복된 이름이면, CONFLICT 예외가 발생하고 save()는 호출되지 않는다.")
        @Test
        void throwsConflict_whenNameIsDuplicated() {
            // Arrange
            String name = "나이키";
            doThrow(new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다."))
                .when(brandDomainService).validateDuplicateName(name);

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                brandApplicationService.register(name)
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(brandRepository, never()).save(any(Brand.class));
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    class UpdateBrand {

        @DisplayName("올바른 이름이 주어지면, 브랜드 이름이 변경된다.")
        @Test
        void updatesBrandName_whenNameIsValid() {
            // Arrange
            Brand brand = Brand.create("나이키");
            when(brandDomainService.getBrand(1L)).thenReturn(brand);

            // Act
            Brand result = brandApplicationService.updateBrand(1L, "아디다스");

            // Assert
            assertThat(result.getName()).isEqualTo("아디다스");
            verify(brandDomainService, times(1)).validateDuplicateNameExcluding("아디다스", 1L);
        }

        @DisplayName("다른 브랜드와 이름이 중복되면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameIsDuplicatedWithOtherBrand() {
            // Arrange
            Brand brand = Brand.create("나이키");
            when(brandDomainService.getBrand(1L)).thenReturn(brand);
            doThrow(new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다."))
                .when(brandDomainService).validateDuplicateNameExcluding("아디다스", 1L);

            // Act & Assert
            CoreException result = assertThrows(CoreException.class, () ->
                brandApplicationService.updateBrand(1L, "아디다스")
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("존재하지 않는 브랜드 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // Arrange
            when(brandDomainService.getBrand(999L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

            // Act & Assert
            CoreException result = assertThrows(CoreException.class, () ->
                brandApplicationService.updateBrand(999L, "아디다스")
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class DeleteBrand {

        @DisplayName("하위 상품이 있으면, 재고 → 상품 → 브랜드 순으로 소프트딜리트된다.")
        @Test
        void softDeletesCascade_whenProductsExist() {
            // Arrange
            Brand brand = Brand.create("나이키");
            when(brandDomainService.getBrand(1L)).thenReturn(brand);
            when(productRepository.findIdsByBrandId(1L)).thenReturn(List.of(10L, 20L));

            // Act
            brandApplicationService.deleteBrand(1L);

            // Assert
            verify(stockRepository, times(1)).softDeleteAllByProductIdIn(List.of(10L, 20L));
            verify(productRepository, times(1)).softDeleteAllByBrandId(1L);
            assertThat(brand.getDeletedAt()).isNotNull();
        }

        @DisplayName("하위 상품이 없으면, 재고/상품 삭제 없이 브랜드만 소프트딜리트된다.")
        @Test
        void softDeletesBrandOnly_whenNoProducts() {
            // Arrange
            Brand brand = Brand.create("나이키");
            when(brandDomainService.getBrand(1L)).thenReturn(brand);
            when(productRepository.findIdsByBrandId(1L)).thenReturn(List.of());

            // Act
            brandApplicationService.deleteBrand(1L);

            // Assert
            verify(stockRepository, never()).softDeleteAllByProductIdIn(anyList());
            verify(productRepository, never()).softDeleteAllByBrandId(anyLong());
            assertThat(brand.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 브랜드 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // Arrange
            when(brandDomainService.getBrand(999L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

            // Act & Assert
            CoreException result = assertThrows(CoreException.class, () ->
                brandApplicationService.deleteBrand(999L)
            );
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
