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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    private static final String BRAND_NAME = "나이키";
    private static final String BRAND_DESCRIPTION = "스포츠 브랜드";

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    private BrandEntity brandOf(Long id, String name, String desc) {
        return BrandEntity.of(id, name, desc, ZonedDateTime.now(), ZonedDateTime.now(), null);
    }

    @DisplayName("브랜드 생성")
    @Nested
    class Create {

        @DisplayName("[ECP] 유효한 name과 description이 주어지면 id가 할당된 브랜드가 생성된다.")
        @Test
        void createsBrand_whenRequestIsValid() {
            // arrange
            BrandEntity saved = brandOf(1L, BRAND_NAME, BRAND_DESCRIPTION);
            given(brandRepository.findByName(BRAND_NAME)).willReturn(Optional.empty());
            given(brandRepository.save(any())).willReturn(saved);

            // act
            BrandEntity result = brandService.create(BRAND_NAME, BRAND_DESCRIPTION);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(BRAND_NAME, result.getName()),
                    () -> assertEquals(BRAND_DESCRIPTION, result.getDescription())
            );
            verify(brandRepository).findByName(BRAND_NAME);
            verify(brandRepository).save(any());
        }

        @DisplayName("[ECP] 이미 존재하는 name이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            // arrange
            given(brandRepository.findByName(BRAND_NAME))
                    .willReturn(Optional.of(brandOf(1L, BRAND_NAME, BRAND_DESCRIPTION)));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.create(BRAND_NAME, BRAND_DESCRIPTION));
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
        }

        @DisplayName("[Error Guessing] 리포지토리가 삭제된 브랜드를 필터링하면 동일 name으로 생성이 성공한다.")
        @Test
        void createsBrand_whenRepositoryExcludesDeletedBrand() {
            // arrange (삭제된 브랜드는 Repository 레이어에서 필터링되어 empty 반환)
            BrandEntity saved = brandOf(2L, BRAND_NAME, BRAND_DESCRIPTION);
            given(brandRepository.findByName(BRAND_NAME)).willReturn(Optional.empty());
            given(brandRepository.save(any())).willReturn(saved);

            // act
            BrandEntity result = brandService.create(BRAND_NAME, BRAND_DESCRIPTION);

            // assert
            assertNotNull(result.getId());
        }
    }

    @DisplayName("브랜드 단건 조회")
    @Nested
    class GetBrand {

        @DisplayName("[ECP] 존재하는 id로 조회하면 BrandEntity를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            // arrange
            BrandEntity existing = brandOf(1L, BRAND_NAME, BRAND_DESCRIPTION);
            given(brandRepository.findById(1L)).willReturn(Optional.of(existing));

            // act
            BrandEntity result = brandService.getBrand(1L);

            // assert
            assertAll(
                    () -> assertEquals(1L, result.getId()),
                    () -> assertEquals(BRAND_NAME, result.getName())
            );
            verify(brandRepository).findById(1L);
        }

        @DisplayName("[ECP] 존재하지 않는 id로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // arrange
            given(brandRepository.findById(999L)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.getBrand(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("브랜드 목록 조회")
    @Nested
    class GetBrands {

        @DisplayName("[ECP] 생성된 브랜드 수만큼 목록이 반환된다.")
        @Test
        void returnsBrandPage() {
            // arrange
            List<BrandEntity> brands = List.of(
                    brandOf(1L, BRAND_NAME, BRAND_DESCRIPTION),
                    brandOf(2L, "아디다스", "독일 스포츠 브랜드")
            );
            PageRequest pageable = PageRequest.of(0, 20);
            given(brandRepository.findAll(pageable)).willReturn(new PageImpl<>(brands, pageable, 2));

            // act
            Page<BrandEntity> result = brandService.getBrands(pageable);

            // assert
            assertEquals(2, result.getTotalElements());
            verify(brandRepository).findAll(pageable);
        }
    }

    @DisplayName("브랜드 수정 — Decision Table: (브랜드 존재) × (name 중복 여부) × (동일 브랜드)")
    @Nested
    class Update {

        @DisplayName("[Decision Table] 브랜드가 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // arrange
            given(brandRepository.findById(999L)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.update(999L, "아디다스", "독일 스포츠 브랜드"));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[Decision Table] 브랜드가 존재하고 name 중복이 없으면 수정된다.")
        @Test
        void updatesBrand_whenNameIsUnique() {
            // arrange
            BrandEntity existing = brandOf(1L, BRAND_NAME, BRAND_DESCRIPTION);
            given(brandRepository.findById(1L)).willReturn(Optional.of(existing));
            given(brandRepository.findByName("아디다스")).willReturn(Optional.empty());
            given(brandRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            BrandEntity result = brandService.update(1L, "아디다스", "독일 스포츠 브랜드");

            // assert
            assertAll(
                    () -> assertEquals("아디다스", result.getName()),
                    () -> assertEquals("독일 스포츠 브랜드", result.getDescription())
            );
            verify(brandRepository).save(any());
        }

        @DisplayName("[Decision Table] 다른 브랜드와 name이 중복되면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameBelongsToAnotherBrand() {
            // arrange
            BrandEntity existing = brandOf(1L, BRAND_NAME, BRAND_DESCRIPTION);
            BrandEntity another = brandOf(2L, "아디다스", "독일 스포츠 브랜드");
            given(brandRepository.findById(1L)).willReturn(Optional.of(existing));
            given(brandRepository.findByName("아디다스")).willReturn(Optional.of(another));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.update(1L, "아디다스", "변경된 설명"));
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
        }

        @DisplayName("[Decision Table] 현재 브랜드와 동일한 name으로 수정하면 성공한다.")
        @Test
        void updatesBrand_whenNameIsSameAsCurrentBrand() {
            // arrange
            BrandEntity existing = brandOf(1L, BRAND_NAME, BRAND_DESCRIPTION);
            given(brandRepository.findById(1L)).willReturn(Optional.of(existing));
            given(brandRepository.findByName(BRAND_NAME)).willReturn(Optional.of(existing));
            given(brandRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            BrandEntity result = brandService.update(1L, BRAND_NAME, "새로운 설명");

            // assert
            assertEquals("새로운 설명", result.getDescription());
        }
    }

    @DisplayName("브랜드 삭제 — State Transition: Active → Deleted")
    @Nested
    class Delete {

        @DisplayName("[State Transition] 존재하는 브랜드를 삭제하면 엔티티가 soft delete 상태로 저장된다.")
        @Test
        void deletesBrand_whenExists() {
            // arrange
            BrandEntity existing = brandOf(1L, BRAND_NAME, BRAND_DESCRIPTION);
            given(brandRepository.findById(1L)).willReturn(Optional.of(existing));
            given(brandRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // act
            brandService.delete(1L);

            // assert
            assertTrue(existing.isDeleted());
            verify(brandRepository).save(any());
        }

        @DisplayName("[ECP] 존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // arrange
            given(brandRepository.findById(999L)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.delete(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }
}
