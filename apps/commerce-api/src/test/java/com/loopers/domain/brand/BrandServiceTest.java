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
    @DisplayName("釉뚮옖??ID濡?議고쉶?섎㈃ ?대떦 釉뚮옖???뺣낫媛 諛섑솚?쒕떎.")
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
