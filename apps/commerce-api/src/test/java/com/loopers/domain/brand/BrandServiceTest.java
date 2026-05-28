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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    private static final Long BRAND_ID = 1L;
    private static final String BRAND_NAME = "나이키";
    private static final String BRAND_DESCRIPTION = "스포츠 브랜드";

    private BrandEntity brandEntity() {
        return BrandEntity.of(BRAND_ID, BRAND_NAME, BRAND_DESCRIPTION, null, null, null);
    }

    @DisplayName("브랜드 생성")
    @Nested
    class Create {

        @DisplayName("유효한 name과 description이 주어지면 브랜드가 생성된다.")
        @Test
        void createsBrand_whenRequestIsValid() {
            // arrange
            BrandEntity saved = brandEntity();
            when(brandRepository.findByName(BRAND_NAME)).thenReturn(Optional.empty());
            when(brandRepository.save(any(BrandEntity.class))).thenReturn(saved);

            // act
            BrandEntity result = brandService.create(BRAND_NAME, BRAND_DESCRIPTION);

            // assert
            assertAll(
                    () -> assertEquals(BRAND_NAME, result.getName()),
                    () -> assertEquals(BRAND_DESCRIPTION, result.getDescription())
            );
            verify(brandRepository, times(1)).save(any(BrandEntity.class));
        }

        @DisplayName("이미 존재하는 name이면 CONFLICT 예외가 발생하고 save는 호출되지 않는다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            // arrange
            when(brandRepository.findByName(BRAND_NAME)).thenReturn(Optional.of(brandEntity()));

            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.create(BRAND_NAME, BRAND_DESCRIPTION));

            // assert
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
            verify(brandRepository, never()).save(any());
        }
    }

    @DisplayName("브랜드 단건 조회")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 id로 조회하면 BrandEntity를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            // arrange
            when(brandRepository.findById(BRAND_ID)).thenReturn(Optional.of(brandEntity()));

            // act
            BrandEntity result = brandService.getBrand(BRAND_ID);

            // assert
            assertEquals(BRAND_ID, result.getId());
        }

        @DisplayName("존재하지 않는 id로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // arrange
            when(brandRepository.findById(BRAND_ID)).thenReturn(Optional.empty());

            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.getBrand(BRAND_ID));

            // assert
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("브랜드 목록 조회")
    @Nested
    class GetBrands {

        @DisplayName("브랜드 목록을 페이지로 반환한다.")
        @Test
        void returnsBrandPage() {
            // arrange
            PageRequest pageable = PageRequest.of(0, 20);
            Page<BrandEntity> page = new PageImpl<>(List.of(brandEntity()), pageable, 1);
            when(brandRepository.findAll(pageable)).thenReturn(page);

            // act
            Page<BrandEntity> result = brandService.getBrands(pageable);

            // assert
            assertAll(
                    () -> assertEquals(1, result.getTotalElements()),
                    () -> assertEquals(BRAND_NAME, result.getContent().get(0).getName())
            );
        }
    }

    @DisplayName("브랜드 수정")
    @Nested
    class Update {

        @DisplayName("유효한 id와 name, description이 주어지면 브랜드가 수정된다.")
        @Test
        void updatesBrand_whenRequestIsValid() {
            // arrange
            String newName = "아디다스";
            String newDescription = "독일 스포츠 브랜드";
            BrandEntity existing = brandEntity();
            when(brandRepository.findById(BRAND_ID)).thenReturn(Optional.of(existing));
            when(brandRepository.findByName(newName)).thenReturn(Optional.empty());
            when(brandRepository.save(existing)).thenReturn(existing);

            // act
            BrandEntity result = brandService.update(BRAND_ID, newName, newDescription);

            // assert
            assertAll(
                    () -> assertEquals(newName, result.getName()),
                    () -> assertEquals(newDescription, result.getDescription())
            );
            verify(brandRepository, times(1)).save(existing);
        }

        @DisplayName("존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // arrange
            when(brandRepository.findById(BRAND_ID)).thenReturn(Optional.empty());

            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.update(BRAND_ID, "아디다스", "독일 스포츠 브랜드"));

            // assert
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("다른 브랜드와 name이 중복되면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameBelongsToAnotherBrand() {
            // arrange
            Long anotherBrandId = 2L;
            BrandEntity another = BrandEntity.of(anotherBrandId, BRAND_NAME, BRAND_DESCRIPTION, null, null, null);
            when(brandRepository.findById(BRAND_ID)).thenReturn(Optional.of(brandEntity()));
            when(brandRepository.findByName(BRAND_NAME)).thenReturn(Optional.of(another));

            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.update(BRAND_ID, BRAND_NAME, BRAND_DESCRIPTION));

            // assert
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
        }

        @DisplayName("현재 브랜드와 동일한 name으로 수정하면 성공한다. (자기 자신과 중복 허용)")
        @Test
        void updatesBrand_whenNameIsSameAsCurrentBrand() {
            // arrange
            BrandEntity existing = brandEntity();
            when(brandRepository.findById(BRAND_ID)).thenReturn(Optional.of(existing));
            when(brandRepository.findByName(BRAND_NAME)).thenReturn(Optional.of(existing));
            when(brandRepository.save(existing)).thenReturn(existing);

            // act
            BrandEntity result = brandService.update(BRAND_ID, BRAND_NAME, "새로운 설명");

            // assert
            assertEquals(BRAND_NAME, result.getName());
        }
    }

    @DisplayName("브랜드 삭제")
    @Nested
    class Delete {

        @DisplayName("존재하는 id이면 soft delete 후 저장된다.")
        @Test
        void deletesBrand_whenBrandExists() {
            // arrange
            BrandEntity existing = brandEntity();
            when(brandRepository.findById(BRAND_ID)).thenReturn(Optional.of(existing));
            when(brandRepository.save(existing)).thenReturn(existing);

            // act
            brandService.delete(BRAND_ID);

            // assert
            assertNotNull(existing.getDeletedAt());
            verify(brandRepository, times(1)).save(existing);
        }

        @DisplayName("존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // arrange
            when(brandRepository.findById(BRAND_ID)).thenReturn(Optional.empty());

            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.delete(BRAND_ID));

            // assert
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }
}
