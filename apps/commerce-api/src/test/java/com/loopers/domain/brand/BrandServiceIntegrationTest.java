package com.loopers.domain.brand;

import com.loopers.application.brand.BrandService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @DisplayName("브랜드 목록을 조회할 때,")
    @Nested
    class GetAll {

        @DisplayName("등록된 브랜드가 페이지네이션되어 반환된다.")
        @Test
        void returnsPaginatedBrands_whenBrandsExist() {
            // arrange
            brandJpaRepository.save(new BrandModel("나이키"));
            brandJpaRepository.save(new BrandModel("아디다스"));
            brandJpaRepository.save(new BrandModel("뉴발란스"));

            // act
            Page<BrandModel> result = brandService.getAll(PageRequest.of(0, 2));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }

        @DisplayName("삭제된 브랜드는 목록에 포함되지 않는다.")
        @Test
        void excludesDeletedBrands_whenBrandsAreSoftDeleted() {
            // arrange
            BrandModel active = brandJpaRepository.save(new BrandModel("나이키"));
            BrandModel deleted = brandJpaRepository.save(new BrandModel("아디다스"));
            deleted.delete();
            brandJpaRepository.save(deleted);

            // act
            Page<BrandModel> result = brandService.getAll(PageRequest.of(0, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(active.getId());
        }
    }

    @DisplayName("브랜드를 단건 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("삭제된 브랜드를 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandIsSoftDeleted() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키"));
            brand.delete();
            brandJpaRepository.save(brand);

            // act & assert
            assertThatThrownBy(() -> brandService.getById(brand.getId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 등록할 때,")
    @Nested
    class Create {

        @DisplayName("이미 존재하는 이름으로 등록하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            // arrange
            brandJpaRepository.save(new BrandModel("나이키"));

            // act & assert
            assertThatThrownBy(() -> brandService.create(new BrandModel("나이키")))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT);
        }
    }
}
