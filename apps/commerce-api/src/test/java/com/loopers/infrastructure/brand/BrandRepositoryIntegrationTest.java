package com.loopers.infrastructure.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class BrandRepositoryIntegrationTest {

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel createBrand(String name) {
        return BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build();
    }

    @DisplayName("브랜드를 저장할 때,")
    @Nested
    class Save {

        @DisplayName("저장하면 식별자가 부여되고 활성 이름으로 조회된다.")
        @Test
        void assignsId_andBecomesActive() {
            // arrange & act
            BrandModel savedBrand = brandRepository.save(createBrand("감성 브랜드"));

            // assert
            assertAll(
                () -> assertThat(savedBrand.getId()).isNotNull(),
                () -> assertThat(brandRepository.existsActiveByName("감성 브랜드")).isTrue()
            );
        }
    }

    @DisplayName("설명을 담아 저장한 브랜드를 재조회할 때,")
    @Nested
    class FindByIdDescription {

        @DisplayName("저장한 설명 값이 그대로 보존된다.")
        @Test
        void preservesDescription_whenSavedWithValue() {
            // arrange
            String description = "감성을 담은 브랜드";
            BrandModel savedBrand = brandRepository.save(
                BrandModel.builder().rawName("감성 브랜드").rawDescription(description).build()
            );

            // act
            BrandModel reloadedBrand = brandJpaRepository.findById(savedBrand.getId()).orElseThrow();

            // assert
            assertThat(reloadedBrand.getDescription()).isEqualTo(description);
        }
    }

    @DisplayName("활성 이름 존재 여부를 조회할 때,")
    @Nested
    class ExistsActiveByName {

        @DisplayName("같은 이름의 삭제되지 않은 브랜드가 있으면 true, 없으면 false를 반환한다.")
        @Test
        void returnsTrueForActiveName_andFalseOtherwise() {
            // arrange
            brandRepository.save(createBrand("감성 브랜드"));

            // act & assert
            assertAll(
                () -> assertThat(brandRepository.existsActiveByName("감성 브랜드")).isTrue(),
                () -> assertThat(brandRepository.existsActiveByName("미등록 브랜드")).isFalse()
            );
        }

        @DisplayName("같은 이름의 브랜드가 삭제된 상태로만 존재하면 false를 반환한다.")
        @Test
        void returnsFalse_whenSameNameBrandIsDeleted() {
            // arrange
            BrandModel savedBrand = brandRepository.save(createBrand("감성 브랜드"));
            savedBrand.delete();
            brandJpaRepository.saveAndFlush(savedBrand);

            // act & assert
            assertThat(brandRepository.existsActiveByName("감성 브랜드")).isFalse();
        }
    }
}
