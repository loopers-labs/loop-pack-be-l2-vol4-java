package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(brandRepository);
    }

    @DisplayName("브랜드를 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("유효한 요청이면, 브랜드를 저장한다.")
        @Test
        void savesBrand_whenRequestIsValid() {
            // arrange
            when(brandRepository.save(any(BrandModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            BrandModel result = brandService.createBrand("Loopers", "감성 이커머스 브랜드");

            // assert
            ArgumentCaptor<BrandModel> brandCaptor = ArgumentCaptor.forClass(BrandModel.class);
            verify(brandRepository).save(brandCaptor.capture());

            BrandModel savedBrand = brandCaptor.getValue();
            assertAll(
                () -> assertThat(result).isSameAs(savedBrand),
                () -> assertThat(savedBrand.getName()).isEqualTo("Loopers"),
                () -> assertThat(savedBrand.getDescription()).isEqualTo("감성 이커머스 브랜드")
            );
        }
    }

    @DisplayName("브랜드를 조회할 때, ")
    @Nested
    class Get {
        @DisplayName("브랜드가 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // arrange
            when(brandRepository.find(1L)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.getBrand(1L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class Delete {
        @DisplayName("존재하는 브랜드이면, 삭제 상태로 변경한 뒤 저장한다.")
        @Test
        void savesDeletedBrand_whenBrandExists() {
            // arrange
            BrandModel brand = new BrandModel("Loopers", "감성 이커머스 브랜드");
            when(brandRepository.find(1L)).thenReturn(Optional.of(brand));

            // act
            brandService.deleteBrand(1L);

            // assert
            assertAll(
                () -> assertThat(brand.isVisible()).isFalse(),
                () -> verify(brandRepository).save(brand)
            );
        }

        @DisplayName("브랜드가 없으면, 저장하지 않는다.")
        @Test
        void doesNotSave_whenBrandDoesNotExist() {
            // arrange
            when(brandRepository.find(1L)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.deleteBrand(1L);
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                () -> verify(brandRepository, never()).save(any())
            );
        }
    }
}
