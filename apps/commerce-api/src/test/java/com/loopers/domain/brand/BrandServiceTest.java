package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    @DisplayName("브랜드 생성 시, ")
    @Nested
    class Create {

        @DisplayName("이름이 중복되지 않으면, 저장하고 저장된 브랜드를 반환한다.")
        @Test
        void savesAndReturnsBrand_whenNameIsNotDuplicated() {
            // given
            String name = "나이키";
            String description = "Just Do It";
            given(brandRepository.existsByName(name)).willReturn(false);
            given(brandRepository.save(any(BrandModel.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            BrandModel result = brandService.create(name, description);

            // then
            assertAll(
                () -> assertThat(result.getName()).isEqualTo(name),
                () -> assertThat(result.getDescription()).isEqualTo(description)
            );
            verify(brandRepository).save(any(BrandModel.class));
        }

        @DisplayName("이름이 이미 존재하면, DUPLICATE_BRAND_NAME 예외를 던진다.")
        @Test
        void throwsDuplicateBrandName_whenNameAlreadyExists() {
            // given
            String name = "나이키";
            given(brandRepository.existsByName(name)).willReturn(true);

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> brandService.create(name, "Just Do It"));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.DUPLICATE_BRAND_NAME);
        }

        @DisplayName("이름이 이미 존재하면, 저장을 시도하지 않는다.")
        @Test
        void doesNotInvokeSave_whenNameAlreadyExists() {
            // given
            String name = "나이키";
            given(brandRepository.existsByName(name)).willReturn(true);

            // when
            assertThrows(CoreException.class, () -> brandService.create(name, "Just Do It"));

            // then
            verify(brandRepository, never()).save(any(BrandModel.class));
        }
    }

    @DisplayName("브랜드 수정 시, ")
    @Nested
    class Update {

        @DisplayName("이름과 설명이 모두 유효하면, 둘 다 변경된다.")
        @Test
        void updatesNameAndDescription_whenBothAreGiven() {
            // given
            Long brandId = 1L;
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            given(brandRepository.findActive(brandId)).willReturn(Optional.of(brand));
            given(brandRepository.existsByNameAndIdNot("아디다스", brandId)).willReturn(false);

            // when
            BrandModel result = brandService.update(brandId, "아디다스", "Impossible is Nothing");

            // then
            assertAll(
                () -> assertThat(result.getName()).isEqualTo("아디다스"),
                () -> assertThat(result.getDescription()).isEqualTo("Impossible is Nothing")
            );
        }

        @DisplayName("이름만 전달되면, 이름만 변경되고 설명은 유지된다.")
        @Test
        void updatesOnlyName_whenDescriptionIsNull() {
            // given
            Long brandId = 1L;
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            given(brandRepository.findActive(brandId)).willReturn(Optional.of(brand));
            given(brandRepository.existsByNameAndIdNot("아디다스", brandId)).willReturn(false);

            // when
            BrandModel result = brandService.update(brandId, "아디다스", null);

            // then
            assertAll(
                () -> assertThat(result.getName()).isEqualTo("아디다스"),
                () -> assertThat(result.getDescription()).isEqualTo("Just Do It")
            );
        }

        @DisplayName("설명만 전달되면, 설명만 변경되고 이름은 유지된다.")
        @Test
        void updatesOnlyDescription_whenNameIsNull() {
            // given
            Long brandId = 1L;
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            given(brandRepository.findActive(brandId)).willReturn(Optional.of(brand));

            // when
            BrandModel result = brandService.update(brandId, null, "새 슬로건");

            // then
            assertAll(
                () -> assertThat(result.getName()).isEqualTo("나이키"),
                () -> assertThat(result.getDescription()).isEqualTo("새 슬로건")
            );
        }

        @DisplayName("새 이름이 다른 브랜드와 중복되면, DUPLICATE_BRAND_NAME 예외를 던진다.")
        @Test
        void throwsDuplicateBrandName_whenNewNameIsTakenByAnotherBrand() {
            // given
            Long brandId = 1L;
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            given(brandRepository.findActive(brandId)).willReturn(Optional.of(brand));
            given(brandRepository.existsByNameAndIdNot("아디다스", brandId)).willReturn(true);

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> brandService.update(brandId, "아디다스", null));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.DUPLICATE_BRAND_NAME);
        }

        @DisplayName("새 이름이 자기 자신의 현재 이름과 같으면, 중복 체크를 통과한다.")
        @Test
        void doesNotThrow_whenNewNameIsSameAsItsOwn() {
            // given
            Long brandId = 1L;
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            given(brandRepository.findActive(brandId)).willReturn(Optional.of(brand));
            given(brandRepository.existsByNameAndIdNot("나이키", brandId)).willReturn(false);

            // when
            BrandModel result = brandService.update(brandId, "나이키", null);

            // then
            assertThat(result.getName()).isEqualTo("나이키");
        }

        @DisplayName("활성 상태가 아닌 (존재하지 않거나 삭제된) 브랜드이면, BRAND_NOT_FOUND 예외를 던진다.")
        @Test
        void throwsBrandNotFound_whenBrandIsNotActive() {
            // given
            Long brandId = 999L;
            given(brandRepository.findActive(brandId)).willReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> brandService.update(brandId, "아디다스", null));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }
    }

    @DisplayName("활성 브랜드 조회 시, ")
    @Nested
    class GetActive {

        @DisplayName("활성 상태인 브랜드가 존재하면, 해당 브랜드를 반환한다.")
        @Test
        void returnsBrand_whenActiveBrandExists() {
            // given
            Long brandId = 1L;
            BrandModel brand = new BrandModel("나이키", "Just Do It");
            given(brandRepository.findActive(brandId)).willReturn(Optional.of(brand));

            // when
            BrandModel result = brandService.getActive(brandId);

            // then
            assertThat(result).isSameAs(brand);
        }

        @DisplayName("활성 브랜드가 존재하지 않으면, BRAND_NOT_FOUND 예외를 던진다.")
        @Test
        void throwsBrandNotFound_whenActiveBrandDoesNotExist() {
            // given
            Long brandId = 999L;
            given(brandRepository.findActive(brandId)).willReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> brandService.getActive(brandId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
        }
    }
}
