package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @InjectMocks
    private BrandService brandService;

    @Mock
    private BrandRepository brandRepository;

    private BrandModel brand;

    @BeforeEach
    void setUp() {
        brand = new BrandModel("Nike", "스포츠 브랜드");
    }

    @DisplayName("create()를 호출할 때,")
    @Nested
    class Create {

        @DisplayName("중복되지 않은 브랜드명으로 등록 시 저장된 BrandInfo가 반환된다.")
        @Test
        void returnsBrandInfo_whenNameIsUnique() {
            // arrange
            BrandCreateCommand command = new BrandCreateCommand("Nike", "스포츠 브랜드");
            given(brandRepository.existsActiveByName("Nike")).willReturn(false);
            given(brandRepository.save(any(BrandModel.class))).willReturn(brand);

            // act
            BrandInfo result = brandService.create(command);

            // assert
            assertThat(result.name()).isEqualTo("Nike");
            then(brandRepository).should().save(any(BrandModel.class));
        }

        @DisplayName("이미 존재하는 브랜드명으로 등록 시 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameIsDuplicated() {
            // arrange
            BrandCreateCommand command = new BrandCreateCommand("Nike", "스포츠 브랜드");
            given(brandRepository.existsActiveByName("Nike")).willReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brandService.create(command)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            then(brandRepository).should(never()).save(any());
        }
    }

    @DisplayName("getById()를 호출할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 브랜드 ID 조회 시 BrandInfo가 반환된다.")
        @Test
        void returnsBrandInfo_whenBrandExists() {
            // arrange
            given(brandRepository.findById(1L)).willReturn(Optional.of(brand));

            // act
            BrandInfo result = brandService.getById(1L);

            // assert
            assertThat(result.name()).isEqualTo("Nike");
        }

        @DisplayName("존재하지 않는 ID 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // arrange
            given(brandRepository.findById(999L)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brandService.getById(999L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("소프트딜리트된 브랜드 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandIsDeleted() {
            // arrange
            brand.delete();
            given(brandRepository.findById(1L)).willReturn(Optional.of(brand));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brandService.getById(1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getAll()를 호출할 때,")
    @Nested
    class GetAll {

        @DisplayName("활성 브랜드 목록을 페이지로 반환한다.")
        @Test
        void returnsPageOfBrandInfo_whenBrandsExist() {
            // arrange
            Pageable pageable = PageRequest.of(0, 20);
            Page<BrandModel> page = new PageImpl<>(List.of(brand), pageable, 1);
            given(brandRepository.findAllActive(pageable)).willReturn(page);

            // act
            Page<BrandInfo> result = brandService.getAll(pageable);

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("Nike");
        }
    }

    @DisplayName("update()를 호출할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 새 이름으로 수정 시 수정된 BrandInfo가 반환된다.")
        @Test
        void returnsBrandInfo_whenUpdateIsValid() {
            // arrange
            BrandUpdateCommand command = new BrandUpdateCommand("Adidas", "독일 스포츠 브랜드");
            given(brandRepository.findById(1L)).willReturn(Optional.of(brand));
            given(brandRepository.existsActiveByName("Adidas")).willReturn(false);
            given(brandRepository.save(brand)).willReturn(brand);

            // act
            BrandInfo result = brandService.update(1L, command);

            // assert
            assertThat(result.name()).isEqualTo("Adidas");
        }

        @DisplayName("이름 변경 없이 설명만 수정 시 중복 확인을 건너뛰고 정상 처리된다.")
        @Test
        void updatesDescription_withoutUniquenessCheck_whenNameIsUnchanged() {
            // arrange
            BrandUpdateCommand command = new BrandUpdateCommand("Nike", "새 설명");
            given(brandRepository.findById(1L)).willReturn(Optional.of(brand));
            given(brandRepository.save(brand)).willReturn(brand);

            // act
            BrandInfo result = brandService.update(1L, command);

            // assert
            assertThat(result.name()).isEqualTo("Nike");
            then(brandRepository).should(never()).existsActiveByName(any());
        }

        @DisplayName("존재하지 않는 ID 수정 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // arrange
            given(brandRepository.findById(999L)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brandService.update(999L, new BrandUpdateCommand("Nike", "설명"))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 사용 중인 이름으로 수정 시 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNewNameIsDuplicated() {
            // arrange
            BrandUpdateCommand command = new BrandUpdateCommand("Adidas", "설명");
            given(brandRepository.findById(1L)).willReturn(Optional.of(brand));
            given(brandRepository.existsActiveByName("Adidas")).willReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brandService.update(1L, command)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            then(brandRepository).should(never()).save(any());
        }
    }

    @DisplayName("delete()를 호출할 때,")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드 삭제 시 소프트딜리트 후 저장된다.")
        @Test
        void softDeletesBrand_whenBrandExists() {
            // arrange
            given(brandRepository.findById(1L)).willReturn(Optional.of(brand));
            given(brandRepository.save(brand)).willReturn(brand);

            // act
            brandService.delete(1L);

            // assert
            assertThat(brand.isDeleted()).isTrue();
            then(brandRepository).should().save(brand);
        }

        @DisplayName("존재하지 않는 ID 삭제 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // arrange
            given(brandRepository.findById(999L)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brandService.delete(999L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
