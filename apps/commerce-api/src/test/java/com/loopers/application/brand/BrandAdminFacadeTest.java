package com.loopers.application.brand;

import com.loopers.application.product.ProductRepository;
import com.loopers.domain.brand.BrandModel;
import com.loopers.support.error.CoreException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandAdminFacadeTest {

    @InjectMocks
    private BrandAdminFacade brandAdminFacade;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductRepository productRepository;

    @Test
    @DisplayName("브랜드 정보를 입력하면 브랜드가 정상적으로 등록된다.")
    void registerBrand_ShouldSaveBrand() {
        // given
        String name = "Nike";
        BrandModel brand = new BrandModel(name);
        ReflectionTestUtils.setField(brand, "id", 1L);
        
        given(brandRepository.save(any(BrandModel.class))).willReturn(brand);

        // when
        Long brandId = brandAdminFacade.registerBrand(name);

        // then
        assertThat(brandId).isEqualTo(1L);
    }

    @Test
    @DisplayName("브랜드 목록 조회를 요청하면 전체 브랜드 목록을 반환한다.")
    void getBrands_ShouldReturnBrandList() {
        // given
        BrandModel brand1 = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand1, "id", 1L);
        BrandModel brand2 = new BrandModel("Adidas");
        ReflectionTestUtils.setField(brand2, "id", 2L);
        
        given(brandRepository.findAll()).willReturn(List.of(brand1, brand2));

        // when
        List<BrandModel> brands = brandAdminFacade.getBrands();

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
        brandAdminFacade.updateBrand(brandId, newName);

        // then
        assertThat(brand.getName()).isEqualTo(newName);
        verify(brandRepository).save(brand);
    }

    @Test
    @DisplayName("존재하지 않는 브랜드의 수정을 요청하면 예외가 발생한다.")
    void updateBrand_NonExistent_ShouldThrowException() {
        // given
        Long brandId = 999L;
        given(brandRepository.findById(brandId)).willReturn(Optional.empty());

        // when & then
        assertThrows(CoreException.class, () -> 
                brandAdminFacade.updateBrand(brandId, "New Name")
        );
    }

    @Test
    @DisplayName("브랜드를 삭제하면 논리 삭제(isDeleted=true) 처리되고 연결된 상품 목록도 삭제된다.")
    void deleteBrand_ShouldMarkAsDeletedAndRemoveProducts() {
        // given
        Long brandId = 1L;
        BrandModel brand = new BrandModel("Nike");
        ReflectionTestUtils.setField(brand, "id", brandId);
        
        given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

        // when
        brandAdminFacade.deleteBrand(brandId);

        // then
        assertThat(brand.isDeleted()).isTrue();
        verify(brandRepository).save(brand);
        verify(productRepository).deleteByBrandId(brandId);
    }

    @Test
    @DisplayName("존재하지 않는 브랜드의 삭제를 요청하면 예외가 발생한다.")
    void deleteBrand_NonExistent_ShouldThrowException() {
        // given
        Long brandId = 999L;
        given(brandRepository.findById(brandId)).willReturn(Optional.empty());

        // when & then
        assertThrows(CoreException.class, () -> 
                brandAdminFacade.deleteBrand(brandId)
        );
    }
}
