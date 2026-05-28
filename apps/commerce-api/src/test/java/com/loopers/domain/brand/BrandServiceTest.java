package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BrandService 순수 단위 테스트 — Repository를 mock으로 격리해 DB 없이
 * 활성/비활성 노출 규칙, 수정/삭제 흐름, batch 조회 위임을 검증한다.
 * (실제 영속·중복 제약 등은 BrandServiceIntegrationTest가 Testcontainers로 검증)
 */
class BrandServiceTest {

    private static final Long BRAND_ID = 7L;

    private BrandRepository brandRepository;
    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandRepository = mock(BrandRepository.class);
        brandService = new BrandService(brandRepository);
    }

    private static BrandModel active(Long id, String name) {
        return BrandModel.reconstitute(id, name, "설명", null);
    }

    private static BrandModel inactive(Long id, String name) {
        return BrandModel.reconstitute(id, name, "설명", ZonedDateTime.now());
    }

    @Nested
    @DisplayName("브랜드 등록")
    class Register {

        @DisplayName("Repository가 저장한 결과를 그대로 반환한다.")
        @Test
        void given_validInput_when_register_then_returnsSaved() {
            BrandModel saved = active(BRAND_ID, "나이키");
            when(brandRepository.save(any(BrandModel.class))).thenReturn(saved);

            BrandModel result = brandService.register("나이키", "스포츠");

            assertThat(result).isSameAs(saved);
        }
    }

    @Nested
    @DisplayName("활성 브랜드 조회")
    class GetActive {

        @DisplayName("활성이면 반환한다.")
        @Test
        void given_active_when_getActiveBrand_then_returns() {
            when(brandRepository.find(BRAND_ID)).thenReturn(Optional.of(active(BRAND_ID, "나이키")));

            BrandModel result = brandService.getActiveBrand(BRAND_ID);

            assertThat(result.getId()).isEqualTo(BRAND_ID);
        }

        @DisplayName("비활성이면 NOT_FOUND로 통일 응대한다(정보 노출 방지).")
        @Test
        void given_inactive_when_getActiveBrand_then_notFound() {
            when(brandRepository.find(BRAND_ID)).thenReturn(Optional.of(inactive(BRAND_ID, "나이키")));

            Throwable thrown = catchThrowable(() -> brandService.getActiveBrand(BRAND_ID));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND를 던진다.")
        @Test
        void given_missing_when_getActiveBrand_then_notFound() {
            when(brandRepository.find(BRAND_ID)).thenReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> brandService.getActiveBrand(BRAND_ID));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("브랜드 수정")
    class Update {

        @DisplayName("활성 브랜드면 이름·설명을 갱신하고 저장한다.")
        @Test
        void given_active_when_update_then_savesUpdated() {
            BrandModel brand = active(BRAND_ID, "나이키");
            when(brandRepository.find(BRAND_ID)).thenReturn(Optional.of(brand));
            when(brandRepository.save(any(BrandModel.class))).thenAnswer(inv -> inv.getArgument(0));

            BrandModel result = brandService.update(BRAND_ID, "아디다스", "스포츠");

            assertThat(result.getName()).isEqualTo("아디다스");
            verify(brandRepository).save(brand);
        }

        @DisplayName("비활성 브랜드면 NOT_FOUND가 발생하고, 저장하지 않는다.")
        @Test
        void given_inactive_when_update_then_notFound() {
            when(brandRepository.find(BRAND_ID)).thenReturn(Optional.of(inactive(BRAND_ID, "나이키")));

            Throwable thrown = catchThrowable(() -> brandService.update(BRAND_ID, "x", "y"));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(brandRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("브랜드 삭제(soft)")
    class Delete {

        @DisplayName("존재하면 soft delete 한 뒤 저장한다.")
        @Test
        void given_existing_when_deleteBrand_then_savesInactive() {
            BrandModel brand = active(BRAND_ID, "나이키");
            when(brandRepository.find(BRAND_ID)).thenReturn(Optional.of(brand));
            when(brandRepository.save(any(BrandModel.class))).thenAnswer(inv -> inv.getArgument(0));

            brandService.deleteBrand(BRAND_ID);

            assertThat(brand.isActive()).isFalse();
            verify(brandRepository).save(brand);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND가 발생한다.")
        @Test
        void given_missing_when_deleteBrand_then_notFound() {
            when(brandRepository.find(BRAND_ID)).thenReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> brandService.deleteBrand(BRAND_ID));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(brandRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("batch 조회")
    class FindByIds {

        @DisplayName("Repository 반환을 그대로 위임한다.")
        @Test
        void delegates_findByIds() {
            List<Long> ids = List.of(1L, 2L);
            List<BrandModel> expected = List.of(active(1L, "나이키"), active(2L, "아디다스"));
            when(brandRepository.findByIds(ids)).thenReturn(expected);

            List<BrandModel> result = brandService.findByIds(ids);

            assertThat(result).isSameAs(expected);
        }
    }
}
