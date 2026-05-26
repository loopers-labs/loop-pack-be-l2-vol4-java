package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandServiceTest {

    private BrandRepository brandRepository;
    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandRepository = new FakeBrandRepository();
        brandService = new BrandService(brandRepository);
    }

    @DisplayName("브랜드를 생성하면, 식별자가 부여되어 저장된다.")
    @Test
    void createsBrand() {
        Brand brand = brandService.createBrand("나이키", "스포츠 브랜드");

        assertThat(brand.getId()).isNotNull();
        assertThat(brandService.getBrand(brand.getId()).getName()).isEqualTo("나이키");
    }

    @DisplayName("존재하지 않는 브랜드를 조회하면, NOT_FOUND 예외가 발생한다.")
    @Test
    void throwsNotFound_whenBrandDoesNotExist() {
        CoreException result = assertThrows(CoreException.class, () -> brandService.getBrand(999L));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @DisplayName("브랜드 존재 여부를 확인할 수 있다.")
    @Test
    void checksBrandExistence() {
        Brand brand = brandService.createBrand("아디다스", null);

        assertThat(brandService.existsBrand(brand.getId())).isTrue();
        assertThat(brandService.existsBrand(999L)).isFalse();
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("이름과 설명이 변경된다.")
        @Test
        void updatesBrand() {
            Brand brand = brandService.createBrand("나이키", "설명");

            brandService.updateBrand(brand.getId(), "뉴발란스", "새 설명");

            Brand updated = brandService.getBrand(brand.getId());
            assertThat(updated.getName()).isEqualTo("뉴발란스");
            assertThat(updated.getDescription()).isEqualTo("새 설명");
        }
    }
}
