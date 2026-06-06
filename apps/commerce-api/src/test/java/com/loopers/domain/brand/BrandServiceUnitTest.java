package com.loopers.domain.brand;

import com.loopers.domain.brand.enums.BrandStatus;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandServiceUnitTest {

    private InMemoryBrandRepository brandRepository;
    @Mock private ProductService productService;
    private BrandService sut;

    private static final Long BRAND_ID = 0L; // BaseEntity.id 기본값
    private static final Long NON_EXISTENT_ID = 999L;
    private static final String DEFAULT_NAME = "나이키";
    private static final String OTHER_NAME = "아디다스";

    @BeforeEach
    void setUp() {
        brandRepository = new InMemoryBrandRepository();
        sut = new BrandService(brandRepository, productService);
    }

    private void saveDefaultBrand() {
        brandRepository.save(new BrandModel(DEFAULT_NAME));
    }

    @DisplayName("브랜드 생성 시,")
    @Nested
    class Create {

        @DisplayName("유효한 이름이면, 브랜드가 생성된다.")
        @Test
        void returnsBrand_whenNameIsValid() {
            BrandModel result = sut.create(DEFAULT_NAME);

            assertThat(result.getName()).isEqualTo(DEFAULT_NAME);
            assertThat(result.getStatus()).isEqualTo(BrandStatus.ACTIVE);
        }

        @DisplayName("이미 존재하는 브랜드명이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            saveDefaultBrand();

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.create(DEFAULT_NAME));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("브랜드 조회 시,")
    @Nested
    class Get {

        @DisplayName("브랜드가 존재하면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            saveDefaultBrand();

            BrandModel result = sut.get(BRAND_ID);

            assertThat(result.getName()).isEqualTo(DEFAULT_NAME);
        }

        @DisplayName("브랜드가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.get(NON_EXISTENT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 수정 시,")
    @Nested
    class Update {

        @DisplayName("유효한 이름이면, 브랜드명이 변경된다.")
        @Test
        void updatesBrandName_whenNameIsValid() {
            saveDefaultBrand();

            BrandModel result = sut.update(BRAND_ID, OTHER_NAME);

            assertThat(result.getName()).isEqualTo(OTHER_NAME);
        }

        @DisplayName("이미 존재하는 브랜드명이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            saveDefaultBrand();

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.update(BRAND_ID, DEFAULT_NAME));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("브랜드가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.update(NON_EXISTENT_ID, OTHER_NAME));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 삭제 시,")
    @Nested
    class Delete {

        @DisplayName("브랜드가 존재하면, 브랜드가 비활성화된다.")
        @Test
        void deactivatesBrand_whenBrandExists() {
            saveDefaultBrand();

            sut.delete(BRAND_ID);

            BrandModel brand = sut.get(BRAND_ID);
            assertThat(brand.getStatus()).isEqualTo(BrandStatus.INACTIVE);
        }

        @DisplayName("브랜드 삭제 시, 해당 브랜드의 상품이 판매 중단 처리된다.")
        @Test
        void suspendsProducts_whenBrandIsDeleted() {
            saveDefaultBrand();
            sut.delete(BRAND_ID);
            verify(productService).suspendAllByBrandId(BRAND_ID);
        }

        @DisplayName("브랜드가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.delete(NON_EXISTENT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

}
