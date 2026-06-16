package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.brand.domain.BrandErrorCode;
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
    @DisplayName("getName 은 존재하는 brandId 의 브랜드명을 반환한다")
    void givenExistingBrandId_whenGetName_thenReturnsName() {
        when(brandRepository.findById(1L)).thenReturn(Optional.of(Brand.create("루퍼스", "설명", null)));

        String result = brandReader.getName(1L);

        assertThat(result).isEqualTo("루퍼스");
    }

    @Test
    @DisplayName("getName 은 존재하지 않는 brandId 면 NOT_FOUND 예외가 발생한다")
    void givenNonExistingBrandId_whenGetName_thenThrowsNotFound() {
        when(brandRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandReader.getName(999L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", BrandErrorCode.BRAND_NOT_FOUND);
    }
}
