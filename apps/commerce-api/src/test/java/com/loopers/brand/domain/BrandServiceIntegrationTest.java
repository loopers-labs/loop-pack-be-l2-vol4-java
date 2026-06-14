package com.loopers.brand.domain;

import com.loopers.utils.DatabaseCleanUp;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class BrandServiceIntegrationTest {

    private final BrandService brandService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    BrandServiceIntegrationTest(BrandService brandService, DatabaseCleanUp databaseCleanUp) {
        this.brandService = brandService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드를 생성할 때 ")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 이름과 설명이 주어지면, 브랜드가 저장된다.")
        @Test
        void savesBrand_whenNameAndDescriptionAreValid() {
            // arrange
            String name = "애플";
            String description = "기술과 디자인으로 일상을 새롭게 만드는 브랜드";

            // act
            Brand saved = brandService.createBrand(name, description);

            // assert
            assertAll(
                () -> assertThat(saved.getId()).isNotNull(),
                () -> assertThat(saved.getName().value()).isEqualTo(name),
                () -> assertThat(saved.getDescription()).isEqualTo(description),
                () -> assertThat(saved.getDeletedAt()).isNull()
            );
        }
    }

    @DisplayName("브랜드를 조회할 때 ")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 ID가 주어지면, 브랜드를 반환한다.")
        @Test
        void returnsBrand_whenBrandIdExists() {
            // arrange
            Brand saved = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");

            // act
            Brand found = brandService.getBrand(saved.getId());

            // assert
            assertAll(
                () -> assertThat(found.getId()).isEqualTo(saved.getId()),
                () -> assertThat(found.getName().value()).isEqualTo("애플"),
                () -> assertThat(found.getDescription()).isEqualTo("기술과 디자인으로 일상을 새롭게 만드는 브랜드")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandIdDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> brandService.getBrand(999_999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 수정할 때 ")
    @Nested
    class UpdateBrand {

        @DisplayName("존재하는 브랜드 ID와 유효한 수정 정보가 주어지면, 브랜드 정보가 변경된다.")
        @Test
        void updatesBrand_whenBrandIdExistsAndInputsAreValid() {
            // arrange
            Brand saved = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            String updatedName = "애플 스토어";
            String updatedDescription = "사용자 경험과 서비스를 함께 제공하는 브랜드";

            // act
            Brand updated = brandService.updateBrand(saved.getId(), updatedName, updatedDescription);

            // assert
            assertAll(
                () -> assertThat(updated.getId()).isEqualTo(saved.getId()),
                () -> assertThat(updated.getName().value()).isEqualTo(updatedName),
                () -> assertThat(updated.getDescription()).isEqualTo(updatedDescription)
            );
        }
    }

    @DisplayName("브랜드를 삭제할 때 ")
    @Nested
    class DeleteBrand {

        @DisplayName("존재하는 브랜드 ID가 주어지면, 브랜드를 삭제 상태로 변경한다.")
        @Test
        void deletesBrand_whenBrandIdExists() {
            // arrange
            Brand saved = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");

            // act
            brandService.deleteBrand(saved.getId());

            // assert
            CoreException result = assertThrows(CoreException.class, () -> brandService.getBrand(saved.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
