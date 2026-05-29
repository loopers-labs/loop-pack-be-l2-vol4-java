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
    @DisplayName("브랜드 정보를 입력하면 브랜드가 정상적으로 등록된다.")
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
    @DisplayName("브랜드 목록 조회를 요청하면 전체 브랜드 목록이 반환된다.")
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
    @DisplayName("존재하는 브랜드의 이름을 수정하면 정보가 업데이트된다.")
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
    @DisplayName("존재하지 않는 브랜드의 수정을 요청하면 예외가 발생한다.")
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
    @DisplayName("브랜드를 삭제하면 논리 삭제(isDeleted=true) 처리된다.")
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
    @DisplayName("존재하지 않는 브랜드의 삭제를 요청하면 예외가 발생한다.")
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
