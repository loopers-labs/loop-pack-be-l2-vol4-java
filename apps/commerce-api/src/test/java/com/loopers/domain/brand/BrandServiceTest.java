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
