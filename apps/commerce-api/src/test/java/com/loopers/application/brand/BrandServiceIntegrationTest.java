package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class BrandServiceIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel saveBrand(String name, String description) {
        return brandJpaRepository.save(new BrandModel(name, description));
    }

    @DisplayName("create()를 호출할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 브랜드 등록 시 DB에 저장되고 ID가 부여된 BrandInfo가 반환된다.")
        @Test
        void savesBrand_whenValidCommandProvided() {
            // arrange
            BrandCreateCommand command = new BrandCreateCommand("Nike", "스포츠 브랜드");

            // act
            BrandInfo result = brandService.create(command);

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo("Nike"),
                () -> assertThat(result.description()).isEqualTo("스포츠 브랜드"),
                () -> assertThat(brandJpaRepository.findById(result.id())).isPresent()
            );
        }

        @DisplayName("이미 존재하는 브랜드명으로 등록 시 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameIsDuplicated() {
            // arrange
            saveBrand("Nike", "스포츠 브랜드");

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brandService.create(new BrandCreateCommand("Nike", "다른 설명"))
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("getById()를 호출할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 브랜드 조회 시 BrandInfo가 반환된다.")
        @Test
        void returnsBrandInfo_whenBrandExists() {
            // arrange
            BrandModel saved = saveBrand("Nike", "스포츠 브랜드");

            // act
            BrandInfo result = brandService.getById(saved.getId());

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(saved.getId()),
                () -> assertThat(result.name()).isEqualTo("Nike")
            );
        }

        @DisplayName("존재하지 않는 ID 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
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
            BrandModel saved = saveBrand("Nike", "스포츠 브랜드");
            brandService.delete(saved.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                brandService.getById(saved.getId())
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getAll()를 호출할 때,")
    @Nested
    class GetAll {

        @DisplayName("활성 브랜드만 페이지에 포함된다.")
        @Test
        void returnsOnlyActiveBrands() {
            // arrange
            saveBrand("Nike", "스포츠");
            saveBrand("Adidas", "독일");
            BrandModel deleted = saveBrand("Puma", "삭제됨");
            brandService.delete(deleted.getId());

            // act
            Page<BrandInfo> result = brandService.getAll(PageRequest.of(0, 20));

            // assert
            assertAll(
                () -> assertThat(result.getTotalElements()).isEqualTo(2),
                () -> assertThat(result.getContent()).extracting(BrandInfo::name)
                    .containsExactlyInAnyOrder("Nike", "Adidas")
            );
        }
    }

    @DisplayName("update()를 호출할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 값으로 수정 시 변경된 내용이 DB에 반영된다.")
        @Test
        void updatesPersisted_whenValidCommandProvided() {
            // arrange
            BrandModel saved = saveBrand("Nike", "스포츠 브랜드");

            // act
            BrandInfo result = brandService.update(saved.getId(), new BrandUpdateCommand("Nike Pro", "프리미엄 스포츠"));

            // assert
            BrandModel updated = brandJpaRepository.findById(saved.getId()).orElseThrow();
            assertAll(
                () -> assertThat(result.name()).isEqualTo("Nike Pro"),
                () -> assertThat(updated.getName()).isEqualTo("Nike Pro"),
                () -> assertThat(updated.getDescription()).isEqualTo("프리미엄 스포츠")
            );
        }
    }

    @DisplayName("delete()를 호출할 때,")
    @Nested
    class Delete {

        @DisplayName("브랜드 삭제 시 DB에 소프트딜리트되어 deleted_at이 설정된다.")
        @Test
        void softDeletesBrandInDb_whenCalled() {
            // arrange
            BrandModel saved = saveBrand("Nike", "스포츠 브랜드");

            // act
            brandService.delete(saved.getId());

            // assert
            BrandModel found = brandJpaRepository.findById(saved.getId()).orElseThrow();
            assertThat(found.isDeleted()).isTrue();
        }
    }
}
