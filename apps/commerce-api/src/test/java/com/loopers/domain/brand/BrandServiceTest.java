package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandServiceTest {

    private BrandService brandService;
    private FakeBrandRepository fakeBrandRepository;

    @BeforeEach
    void setUp() {
        fakeBrandRepository = new FakeBrandRepository();
        brandService = new BrandService(fakeBrandRepository);
    }

    @DisplayName("브랜드를 등록할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 이름으로 등록하면, 브랜드가 저장되어 반환된다.")
        @Test
        void savesBrand_whenNameIsValid() {
            // arrange
            BrandModel brand = new BrandModel("나이키");

            // act
            BrandModel saved = brandService.create(brand);

            // assert
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getName()).isEqualTo("나이키");
        }

        @DisplayName("이미 활성 브랜드와 같은 이름으로 등록하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenActiveNameAlreadyExists() {
            // arrange
            brandService.create(new BrandModel("나이키"));

            // act & assert
            assertThatThrownBy(() -> brandService.create(new BrandModel("나이키")))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("삭제된 브랜드와 같은 이름으로 등록하면, 성공한다.")
        @Test
        void savesBrand_whenSameNameExistsButDeleted() {
            // arrange
            BrandModel saved = brandService.create(new BrandModel("나이키"));
            brandService.delete(saved.getId());

            // act
            BrandModel reCreated = brandService.create(new BrandModel("나이키"));

            // assert
            assertThat(reCreated.getId()).isNotNull();
            assertThat(reCreated.getName()).isEqualTo("나이키");
        }
    }

    @DisplayName("브랜드를 단건 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 brandId가 주어지면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandIdExists() {
            // arrange
            BrandModel saved = brandService.create(new BrandModel("나이키"));

            // act
            BrandModel result = brandService.getById(saved.getId());

            // assert
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getName()).isEqualTo("나이키");
        }

        @DisplayName("존재하지 않는 brandId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandIdDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act & assert
            assertThatThrownBy(() -> brandService.getById(nonExistentId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 이름을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 이름으로 수정하면, 이름이 변경된다.")
        @Test
        void updatesName_whenBrandIdExistsAndNameIsValid() {
            // arrange
            BrandModel saved = brandService.create(new BrandModel("나이키"));

            // act
            BrandModel updated = brandService.update(saved.getId(), "아디다스");

            // assert
            assertThat(updated.getName()).isEqualTo("아디다스");
        }

        @DisplayName("존재하지 않는 brandId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandIdDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act & assert
            assertThatThrownBy(() -> brandService.update(nonExistentId, "아디다스"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("다른 브랜드가 사용 중인 이름으로 수정하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameIsUsedByAnotherBrand() {
            // arrange
            brandService.create(new BrandModel("아디다스"));
            BrandModel nike = brandService.create(new BrandModel("나이키"));

            // act & assert
            assertThatThrownBy(() -> brandService.update(nike.getId(), "아디다스"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("자기 자신의 현재 이름과 동일한 이름으로 수정하면, 성공한다.")
        @Test
        void updateSucceeds_whenNameIsSameAsCurrentName() {
            // arrange
            BrandModel saved = brandService.create(new BrandModel("나이키"));

            // act
            BrandModel updated = brandService.update(saved.getId(), "나이키");

            // assert
            assertThat(updated.getName()).isEqualTo("나이키");
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드를 삭제하면, deletedAt이 설정된다.")
        @Test
        void softDeletesBrand_whenBrandIdExists() {
            // arrange
            BrandModel saved = brandService.create(new BrandModel("나이키"));

            // act
            brandService.delete(saved.getId());

            // assert
            BrandModel deleted = fakeBrandRepository.findByIdIgnoringFilter(saved.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 brandId가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandIdDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act & assert
            assertThatThrownBy(() -> brandService.delete(nonExistentId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    // ───────────────────────────────────────────────
    // Fake: DB 없이 비즈니스 로직만 격리 검증
    // @SQLRestriction 동작(soft delete 필터)은 재현하지 않음
    //   → 해당 동작은 BrandServiceIntegrationTest에서 실제 DB로만 검증
    // ───────────────────────────────────────────────
    private static class FakeBrandRepository implements BrandRepository {

        private final Map<Long, BrandModel> store = new HashMap<>();
        private long sequence = 1L;

        @Override
        public BrandModel save(BrandModel brand) {
            setId(brand, sequence++);
            store.put(brand.getId(), brand);
            return brand;
        }

        @Override
        public Optional<BrandModel> findById(Long id) {
            return Optional.ofNullable(store.get(id))
                .filter(b -> b.getDeletedAt() == null);
        }

        @Override
        public boolean existsByName(String name) {
            return store.values().stream()
                .anyMatch(b -> b.getDeletedAt() == null && b.getName().equals(name));
        }

        @Override
        public Page<BrandModel> findAll(PageRequest pageRequest) {
            throw new UnsupportedOperationException("통합 테스트에서 검증");
        }

        /** soft delete 필터를 무시하고 조회 — 삭제 여부 검증용 */
        public Optional<BrandModel> findByIdIgnoringFilter(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        private void setId(BrandModel brand, long id) {
            try {
                var field = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(brand, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
