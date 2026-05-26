package com.loopers.infrastructure.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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

        @DisplayName("같은 이름의 활성 브랜드가 있으면 true, 없으면 false를 반환한다.")
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

    @DisplayName("활성 브랜드를 식별자로 조회할 때,")
    @Nested
    class GetActiveById {

        @DisplayName("활성 브랜드는 반환하고, 삭제됐거나 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void returnsActiveBrand_andThrowsOtherwise() {
            // arrange
            BrandModel activeBrand = brandRepository.save(createBrand("활성 브랜드"));
            BrandModel deletedBrand = brandRepository.save(createBrand("삭제 브랜드"));
            deletedBrand.delete();
            brandJpaRepository.saveAndFlush(deletedBrand);

            // act & assert
            assertAll(
                () -> assertThat(brandRepository.getActiveById(activeBrand.getId()).getId()).isEqualTo(activeBrand.getId()),
                () -> assertThatThrownBy(() -> brandRepository.getActiveById(deletedBrand.getId()))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThatThrownBy(() -> brandRepository.getActiveById(-1L))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND)
            );
        }
    }

    @DisplayName("자신을 제외한 활성 이름 중복을 조회할 때,")
    @Nested
    class ExistsActiveByNameAndIdNot {

        @DisplayName("자기 자신만 같은 이름이면 false를 반환한다.")
        @Test
        void returnsFalse_whenOnlySelfHasName() {
            // arrange
            BrandModel savedBrand = brandRepository.save(createBrand("감성 브랜드"));

            // act & assert
            assertThat(brandRepository.existsActiveByNameAndIdNot("감성 브랜드", savedBrand.getId())).isFalse();
        }

        @DisplayName("같은 이름의 다른 활성 브랜드가 있으면 true를 반환한다.")
        @Test
        void returnsTrue_whenOtherActiveBrandHasSameName() {
            // arrange
            BrandModel existingBrand = brandRepository.save(createBrand("감성 브랜드"));
            BrandModel targetBrand = brandRepository.save(createBrand("다른 브랜드"));

            // act & assert
            assertAll(
                () -> assertThat(brandRepository.existsActiveByNameAndIdNot("감성 브랜드", targetBrand.getId())).isTrue(),
                () -> assertThat(existingBrand.getId()).isNotEqualTo(targetBrand.getId())
            );
        }
    }

    @DisplayName("활성 브랜드를 페이지로 조회할 때,")
    @Nested
    class FindActivePage {

        @DisplayName("삭제된 브랜드를 제외하고 등록 시각 내림차순으로 페이징한다.")
        @Test
        void returnsActivePage_excludingDeleted_sortedByCreatedAtDesc() {
            // arrange
            brandRepository.save(createBrand("브랜드1"));
            brandRepository.save(createBrand("브랜드2"));
            BrandModel deletedBrand = brandRepository.save(createBrand("브랜드3"));
            deletedBrand.delete();
            brandJpaRepository.saveAndFlush(deletedBrand);

            // act
            Page<BrandModel> brandPage = brandRepository.findActiveByPage(0, 10);

            // assert
            assertAll(
                () -> assertThat(brandPage.getTotalElements()).isEqualTo(2),
                () -> assertThat(brandPage.getContent())
                    .extracting(brand -> brand.getName().value())
                    .containsExactlyInAnyOrder("브랜드1", "브랜드2"),
                () -> assertThat(brandPage.getContent())
                    .extracting(BrandModel::getCreatedAt)
                    .isSortedAccordingTo(Comparator.reverseOrder())
            );
        }
    }
}
