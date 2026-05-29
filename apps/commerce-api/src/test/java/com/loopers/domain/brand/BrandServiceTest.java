package com.loopers.domain.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @InjectMocks
    private BrandService brandService;

    @Mock
    private BrandRepository brandRepository;

    @Test
    @DisplayName("브랜드 ID로 조회하면 해당 브랜드 정보가 반환된다.")
    void getBrand_ShouldReturnBrand() {
        // given
        Long brandId = 1L;
        BrandModel brand = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand, "id", brandId);
        
        given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

        // when
        BrandModel result = brandService.getBrand(brandId);

        // then
        assertThat(result.getId()).isEqualTo(brandId);
        assertThat(result.getName()).isEqualTo("Nike");
    }
}
