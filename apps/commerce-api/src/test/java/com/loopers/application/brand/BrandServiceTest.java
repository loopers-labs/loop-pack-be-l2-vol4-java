package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.FakeBrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandServiceTest {

    private BrandService brandService;
    private FakeBrandRepository brandRepository;

    @BeforeEach
    void setUp() {
        brandRepository = new FakeBrandRepository();
        brandService = new BrandService(brandRepository);
    }

    @DisplayName("브랜드를 ID로 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 브랜드면 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            // given
            BrandModel saved = brandRepository.save(new BrandModel("Nike"));

            // when
            BrandModel result = brandService.getById(saved.getId());

            // then
            assertAll(
                    () -> assertThat(result.getId()).isEqualTo(saved.getId()),
                    () -> assertThat(result.getName()).isEqualTo("Nike")
            );
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class, () -> brandService.getById(999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
