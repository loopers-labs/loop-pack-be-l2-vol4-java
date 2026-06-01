package com.loopers.brand.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class BrandFacadeIntegrationTest {

    @Autowired
    private BrandFacade brandFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드를 등록할 때,")
    @Nested
    class CreateBrand {

        @DisplayName("정상 요청이면, DB에 저장되고 BrandInfo를 반환한다.")
        @Test
        void returnsBrandInfo_whenRequestIsValid() {
            // act
            BrandInfo result = brandFacade.createBrand("나이키", "스포츠 브랜드");

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo("나이키"),
                () -> assertThat(result.description()).isEqualTo("스포츠 브랜드")
            );
        }
    }

    @DisplayName("브랜드를 단건 조회할 때,")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 brandId이면, BrandInfo를 반환한다.")
        @Test
        void returnsBrandInfo_whenBrandExists() {
            // arrange
            BrandModel saved = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));

            // act
            BrandInfo result = brandFacade.getBrand(saved.getId());

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(saved.getId()),
                () -> assertThat(result.name()).isEqualTo("나이키"),
                () -> assertThat(result.description()).isEqualTo("스포츠 브랜드")
            );
        }

        @DisplayName("존재하지 않는 brandId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                brandFacade.getBrand(999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 brandId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandIsDeleted() {
            // arrange
            BrandModel saved = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));
            saved.delete();
            brandJpaRepository.save(saved);

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                brandFacade.getBrand(saved.getId())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 수정할 때,")
    @Nested
    class UpdateBrand {

        @DisplayName("정상 요청이면, 수정된 BrandInfo를 반환한다.")
        @Test
        void returnsUpdatedBrandInfo_whenRequestIsValid() {
            // arrange
            BrandModel saved = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));

            // act
            BrandInfo result = brandFacade.updateBrand(saved.getId(), "아디다스", "글로벌 스포츠 브랜드");

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(saved.getId()),
                () -> assertThat(result.name()).isEqualTo("아디다스"),
                () -> assertThat(result.description()).isEqualTo("글로벌 스포츠 브랜드")
            );
        }

        @DisplayName("존재하지 않는 brandId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                brandFacade.updateBrand(999L, "아디다스", "글로벌 스포츠 브랜드")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class DeleteBrand {

        @DisplayName("정상 요청이면, 브랜드가 소프트 딜리트된다.")
        @Test
        void softDeletesBrand_whenRequestIsValid() {
            // arrange
            BrandModel saved = brandJpaRepository.save(new BrandModel("나이키", "스포츠 브랜드"));

            // act
            brandFacade.deleteBrand(saved.getId());

            // assert — 삭제 후 조회 시 NOT_FOUND
            CoreException exception = assertThrows(CoreException.class, () ->
                brandFacade.getBrand(saved.getId())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 brandId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                brandFacade.deleteBrand(999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
