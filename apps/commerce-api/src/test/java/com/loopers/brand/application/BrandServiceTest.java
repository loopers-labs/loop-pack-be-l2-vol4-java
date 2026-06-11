package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandServiceTest {

    private final BrandRepository brandRepository = mock(BrandRepository.class);
    private final BrandReader brandReader = mock(BrandReader.class);
    private final BrandService brandService = new BrandService(brandRepository, brandReader);

    @Test
    @DisplayName("get 은 reader 가 반환한 brand 를 Detail 로 매핑해서 반환한다")
    void givenExistingBrandId_whenGet_thenReturnsBrandDetail() {
        Brand brand = Brand.create("루퍼스", "설명", "https://cdn.loopers.com/l.png");
        when(brandReader.get(1L)).thenReturn(brand);

        BrandResult.Detail result = brandService.get(1L);

        assertAll(
                () -> assertThat(result.name()).isEqualTo("루퍼스"),
                () -> assertThat(result.description()).isEqualTo("설명"),
                () -> assertThat(result.logoUrl()).isEqualTo("https://cdn.loopers.com/l.png")
        );
    }

    @Test
    @DisplayName("getAll 은 repository 의 brand 들을 Detail 리스트로 매핑한다")
    void givenBrands_whenGetAll_thenReturnsBrandDetails() {
        Brand a = Brand.create("A", "설명A", null);
        Brand b = Brand.create("B", "설명B", null);
        when(brandRepository.findAll()).thenReturn(List.of(a, b));

        List<BrandResult.Detail> result = brandService.getAll();

        assertThat(result)
                .extracting(BrandResult.Detail::name)
                .containsExactly("A", "B");
    }
}
