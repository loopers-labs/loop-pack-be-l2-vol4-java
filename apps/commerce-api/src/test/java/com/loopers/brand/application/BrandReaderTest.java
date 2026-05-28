package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandReaderTest {

    private final BrandRepository brandRepository = mock(BrandRepository.class);
    private final BrandReader brandReader = new BrandReader(brandRepository);

    @Test
    @DisplayName("존재하는 brandId 로 조회하면 해당 브랜드를 반환한다")
    void givenExistingBrandId_whenGet_thenReturnsBrand() {
        Brand brand = Brand.create("루퍼스", "설명", null);
        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));

        Brand result = brandReader.get(1L);

        assertThat(result).isSameAs(brand);
    }

    @Test
    @DisplayName("존재하지 않는 brandId 로 조회하면 NOT_FOUND 예외가 발생한다")
    void givenNonExistingBrandId_whenGet_thenThrowsNotFound() {
        when(brandRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandReader.get(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
    }
}
