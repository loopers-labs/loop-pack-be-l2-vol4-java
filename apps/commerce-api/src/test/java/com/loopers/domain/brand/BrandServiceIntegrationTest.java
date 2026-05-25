package com.loopers.domain.brand;

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

    @DisplayName("브랜드를 단건 조회할 때,")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 ID를 주면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenValidIdIsProvided() {
            Brand saved = brandJpaRepository.save(new Brand("브랜드", "설명"));

            Brand result = brandService.getBrand(saved.getId());

            assertAll(
                () -> assertThat(result.getId()).isEqualTo(saved.getId()),
                () -> assertThat(result.getName()).isEqualTo("브랜드")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class,
                () -> brandService.getBrand(9999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드 ID를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandIsSoftDeleted() {
            Brand saved = brandJpaRepository.save(new Brand("브랜드", "설명"));
            saved.delete();
            brandJpaRepository.save(saved);

            CoreException ex = assertThrows(CoreException.class,
                () -> brandService.getBrand(saved.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 목록을 조회할 때,")
    @Nested
    class GetAllBrands {

        @DisplayName("삭제된 브랜드는 제외하고 조회한다.")
        @Test
        void excludesDeletedBrands() {
            brandJpaRepository.save(new Brand("활성 브랜드", "설명"));
            Brand deleted = brandJpaRepository.save(new Brand("삭제 브랜드", "설명"));
            deleted.delete();
            brandJpaRepository.save(deleted);

            Page<Brand> result = brandService.getAllBrands(PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 정보를 주면, 브랜드가 생성된다.")
        @Test
        void createsBrand_whenValidInfoIsProvided() {
            Brand brand = brandService.createBrand("무신사", "무신사 브랜드");

            assertAll(
                () -> assertThat(brand.getId()).isNotNull(),
                () -> assertThat(brand.getName()).isEqualTo("무신사"),
                () -> assertThat(brand.getDescription()).isEqualTo("무신사 브랜드")
            );
        }
    }

    @DisplayName("브랜드 정보를 수정할 때,")
    @Nested
    class UpdateBrand {

        @DisplayName("존재하는 브랜드를 수정하면, 수정된 정보가 반환된다.")
        @Test
        void updatesBrand_whenBrandExists() {
            Brand saved = brandJpaRepository.save(new Brand("원래", "원래 설명"));

            Brand updated = brandService.updateBrand(saved.getId(), "새 이름", "새 설명");

            assertAll(
                () -> assertThat(updated.getName()).isEqualTo("새 이름"),
                () -> assertThat(updated.getDescription()).isEqualTo("새 설명")
            );
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class,
                () -> brandService.updateBrand(9999L, "이름", "설명"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class DeleteBrand {

        @DisplayName("존재하지 않는 브랜드를 삭제하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class,
                () -> brandService.deleteBrand(9999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
