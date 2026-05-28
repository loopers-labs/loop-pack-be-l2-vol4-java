package com.loopers.domain.brand;

import com.loopers.fixture.BrandFixture;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class BrandServiceIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 이름과 설명으로 생성 시, 저장된 브랜드를 반환한다.")
        @Test
        void returnsSavedBrand_whenValidInput() {
            // arrange
            String name = BrandFixture.NAME;
            String description = BrandFixture.DESCRIPTION;

            // act
            BrandModel saved = brandService.create(name, description);

            // assert
            assertAll(
                () -> assertThat(saved.getId()).isNotNull(),
                () -> assertThat(saved.getName()).isEqualTo(name),
                () -> assertThat(saved.getDescription()).isEqualTo(description),
                () -> assertThat(saved.getDeletedAt()).isNull()
            );
        }

        @DisplayName("이미 존재하는 브랜드명으로 생성 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            // arrange
            brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);

            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                brandService.create(BrandFixture.NAME, "다른 설명")
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("삭제된 브랜드와 동일한 이름으로 재등록 시, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenDeletedBrandNameReused() {
            // arrange — 생성 후 소프트딜리트
            BrandModel brand = brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);
            brandService.delete(brand.getId());

            // act & assert — 삭제됐어도 이름 영구 차단 (정책 ⑧)
            CoreException ex = assertThrows(CoreException.class, () ->
                brandService.create(BrandFixture.NAME, "새 설명")
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("브랜드를 단건 조회할 때,")
    @Nested
    class Get {

        @DisplayName("존재하지 않는 ID로 조회 시, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdNotExists() {
            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                brandService.get(UUID.randomUUID())
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드를 어드민용 get으로 조회 시, 반환된다.")
        @Test
        void returnsBrand_whenDeletedAndCalledByAdmin() {
            // arrange
            BrandModel brand = brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);
            brandService.delete(brand.getId());

            // act
            BrandModel found = brandService.get(brand.getId());

            // assert
            assertThat(found.getId()).isEqualTo(brand.getId());
        }

        @DisplayName("삭제된 브랜드를 고객용 getActive로 조회 시, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenDeletedAndCalledByCustomer() {
            // arrange
            BrandModel brand = brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);
            brandService.delete(brand.getId());

            // act & assert
            CoreException ex = assertThrows(CoreException.class, () ->
                brandService.getActive(brand.getId())
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("삭제 후 deletedAt이 기록되고, DB에서 행이 보존된다.")
        @Test
        void softDeletes_whenDeleteCalled() {
            // arrange
            BrandModel brand = brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);

            // act
            brandService.delete(brand.getId());

            // assert — 어드민용 get은 성공, deletedAt 존재
            BrandModel deleted = brandService.get(brand.getId());
            assertThat(deleted.getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("브랜드 목록을 조회할 때,")
    @Nested
    class GetList {

        @DisplayName("페이징 조건에 맞게 브랜드 목록을 반환한다.")
        @Test
        void returnsPagedBrands_whenBrandsExist() {
            // arrange
            brandService.create("나이키", BrandFixture.DESCRIPTION);
            brandService.create("아디다스", BrandFixture.DESCRIPTION);
            brandService.create("뉴발란스", BrandFixture.DESCRIPTION);

            // act
            Page<BrandModel> page = brandService.getList(PageRequest.of(0, 2));

            // assert
            assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(3),
                () -> assertThat(page.getContent()).hasSize(2),
                () -> assertThat(page.getTotalPages()).isEqualTo(2)
            );
        }
    }
}
