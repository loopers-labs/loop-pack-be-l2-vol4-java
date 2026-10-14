package com.loopers.domain.brand;

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
    private FakeBrandRepository fakeRepository;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeBrandRepository();
        brandService = new BrandService(fakeRepository);
    }

    @DisplayName("브랜드를 조회할 때, ")
    @Nested
    class GetBrand {
        @DisplayName("존재하는 브랜드 ID 면, 브랜드를 반환한다.")
        @Test
        void returnsBrand_whenIdExists() {
            // arrange
            BrandModel saved = brandService.createBrand("나이키", "스포츠");

            // act
            BrandModel result = brandService.getBrand(saved.getId());

            // assert
            assertAll(
                () -> assertThat(result.getId()).isEqualTo(saved.getId()),
                () -> assertThat(result.getName()).isEqualTo("나이키")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brandService.getBrand(999L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 존재 여부를 확인할 때, ")
    @Nested
    class RequireExists {
        @DisplayName("존재하지 않는 ID 면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brandService.requireExists(999L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하는 ID 면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenIdExists() {
            // arrange
            BrandModel saved = brandService.createBrand("나이키", "스포츠");

            // act & assert
            brandService.requireExists(saved.getId());
        }
    }
}
