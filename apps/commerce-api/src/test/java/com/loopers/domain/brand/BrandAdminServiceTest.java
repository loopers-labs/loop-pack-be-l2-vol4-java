package com.loopers.domain.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BrandAdminServiceTest {

    @InjectMocks
    private BrandAdminService brandAdminService;

    @Mock
    private BrandRepository brandRepository;

    @Test
    @DisplayName("釉뚮옖???뺣낫瑜??낅젰?섎㈃ 釉뚮옖?쒓? ?뺤긽?곸쑝濡??깅줉?쒕떎.")
    void registerBrand_ShouldSaveBrand() {
        // given
        String name = "Nike";
        BrandModel brand = new BrandModel(name);
        ReflectionTestUtils.setField(brand, "id", 1L);
        
        given(brandRepository.save(any(BrandModel.class))).willReturn(brand);

        // when
        Long brandId = brandAdminService.registerBrand(name);

        // then
        assertThat(brandId).isEqualTo(1L);
    }

    @Test
    @DisplayName("釉뚮옖??紐⑸줉 議고쉶瑜??붿껌?섎㈃ ?꾩껜 釉뚮옖??紐⑸줉??諛섑솚?쒕떎.")
    void getBrands_ShouldReturnBrandList() {
        // given
        BrandModel brand1 = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand1, "id", 1L);
        BrandModel brand2 = new BrandModel("Adidas");
        ReflectionTestUtils.setField(brand2, "id", 2L);
        
        given(brandRepository.findAll()).willReturn(List.of(brand1, brand2));

        // when
        List<BrandModel> brands = brandAdminService.getBrands();

        // then
        assertThat(brands).hasSize(2);
        assertThat(brands).extracting("name").containsExactly("Nike", "Adidas");
    }

    @Test
    @DisplayName("議댁옱?섎뒗 釉뚮옖?쒖쓽 ?대쫫???섏젙?섎㈃ ?뺣낫媛 ?낅뜲?댄듃?쒕떎.")
    void updateBrand_ShouldUpdateName() {
        // given
        Long brandId = 1L;
        String newName = "Nike Pro";
        BrandModel brand = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand, "id", brandId);
        
        given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

        // when
        brandAdminService.updateBrand(brandId, newName);

        // then
        assertThat(brand.getName()).isEqualTo(newName);
    }

    @Test
    @DisplayName("議댁옱?섏? ?딅뒗 釉뚮옖?쒖쓽 ?섏젙???붿껌?섎㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void updateBrand_NonExistent_ShouldThrowException() {
        // given
        Long brandId = 999L;
        given(brandRepository.findById(brandId)).willReturn(Optional.empty());

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.loopers.support.error.CoreException.class, () -> 
                brandAdminService.updateBrand(brandId, "New Name")
        );
    }

    @Test
    @DisplayName("釉뚮옖?쒕? ??젣?섎㈃ ?쇰━ ??젣(isDeleted=true) 泥섎━?쒕떎.")
    void deleteBrand_ShouldMarkAsDeleted() {
        // given
        Long brandId = 1L;
        BrandModel brand = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand, "id", brandId);
        
        given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

        // when
        brandAdminService.deleteBrand(brandId);

        // then
        assertThat(brand.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("議댁옱?섏? ?딅뒗 釉뚮옖?쒖쓽 ??젣瑜??붿껌?섎㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void deleteBrand_NonExistent_ShouldThrowException() {
        // given
        Long brandId = 999L;
        given(brandRepository.findById(brandId)).willReturn(Optional.empty());

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.loopers.support.error.CoreException.class, () -> 
                brandAdminService.deleteBrand(brandId)
        );
    }
}
