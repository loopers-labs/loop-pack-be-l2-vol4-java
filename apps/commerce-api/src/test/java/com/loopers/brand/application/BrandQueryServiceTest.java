package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandErrorCode;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandQueryServiceTest {

    private final BrandRepository brandRepository = mock(BrandRepository.class);
    private final BrandQueryService brandQueryService = new BrandQueryService(brandRepository);

    @Test
    @DisplayName("getBrand 는 brand 를 Detail 로 매핑해서 반환한다")
    void givenExistingBrandId_whenGetBrand_thenReturnsBrandDetail() {
        Brand brand = Brand.create("루퍼스", "설명", "https://cdn.loopers.com/l.png");
        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

        BrandResult.Detail result = brandQueryService.getBrand(1L);

        assertAll(
                () -> assertThat(result.name()).isEqualTo("루퍼스"),
                () -> assertThat(result.description()).isEqualTo("설명"),
                () -> assertThat(result.logoUrl()).isEqualTo("https://cdn.loopers.com/l.png")
        );
    }

    @Test
    @DisplayName("getBrand 는 브랜드가 없으면 NOT_FOUND 를 던진다")
    void givenMissingBrandId_whenGetBrand_thenThrowsNotFound() {
        when(brandRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandQueryService.getBrand(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", BrandErrorCode.BRAND_NOT_FOUND);
    }

    @Test
    @DisplayName("getBrands 는 repository 의 brand 들을 Detail 리스트로 매핑한다")
    void givenBrands_whenGetBrands_thenReturnsBrandDetails() {
        Brand a = Brand.create("A", "설명A", null);
        Brand b = Brand.create("B", "설명B", null);
        when(brandRepository.findAll()).thenReturn(List.of(a, b));

        List<BrandResult.Detail> result = brandQueryService.getBrands();

        assertThat(result)
                .extracting(BrandResult.Detail::name)
                .containsExactly("A", "B");
    }
}
