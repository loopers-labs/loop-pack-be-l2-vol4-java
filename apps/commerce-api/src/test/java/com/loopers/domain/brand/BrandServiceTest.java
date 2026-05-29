package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class BrandServiceTest {

    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(new FakeBrandRepository());
    }

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class CreateBrand {

        @DisplayName("중복되지 않는 이름이면, 브랜드가 저장되고 반환된다.")
        @Test
        void createsBrand_whenNameIsUnique() {
            Brand result = brandService.createBrand("나이키");

            assertAll(
                () -> assertThat(result.getName()).isEqualTo("나이키"),
                () -> assertThat(result.getId()).isNotNull()
            );
        }

        @DisplayName("이미 존재하는 이름이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            brandService.createBrand("나이키");

            CoreException result = assertThrows(CoreException.class,
                () -> brandService.createBrand("나이키"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("브랜드를 단건 조회할 때,")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 ID면, 브랜드를 반환한다.")
        @Test
        void returnsBrand_whenIdExists() {
            Brand saved = brandService.createBrand("나이키");

            Brand found = brandService.getBrand(saved.getId());

            assertThat(found.getName()).isEqualTo("나이키");
        }

        @DisplayName("존재하지 않는 ID면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdNotExists() {
            CoreException result = assertThrows(CoreException.class,
                () -> brandService.getBrand(999L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 목록을 조회할 때,")
    @Nested
    class GetAllBrands {

        @DisplayName("저장된 모든 브랜드를 반환한다.")
        @Test
        void returnsAllBrands() {
            brandService.createBrand("나이키");
            brandService.createBrand("아디다스");

            List<Brand> brands = brandService.getAllBrands();

            assertThat(brands).hasSize(2);
        }

        @DisplayName("브랜드가 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoBrandsExist() {
            assertThat(brandService.getAllBrands()).isEmpty();
        }

        @DisplayName("삭제된 브랜드는 목록에 포함되지 않는다.")
        @Test
        void excludesDeletedBrands() {
            Brand saved = brandService.createBrand("나이키");
            brandService.createBrand("아디다스");
            brandService.deleteBrand(saved.getId());

            List<Brand> brands = brandService.getAllBrands();

            assertThat(brands).hasSize(1);
            assertThat(brands.get(0).getName()).isEqualTo("아디다스");
        }
    }

    @DisplayName("브랜드를 수정할 때,")
    @Nested
    class UpdateBrand {

        @DisplayName("유효한 새 이름이면, 이름이 변경된다.")
        @Test
        void updatesBrand_whenNewNameIsValid() {
            Brand saved = brandService.createBrand("나이키");

            Brand updated = brandService.updateBrand(saved.getId(), "아디다스");

            assertThat(updated.getName()).isEqualTo("아디다스");
        }

        @DisplayName("이미 사용 중인 이름으로 변경하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNewNameAlreadyExists() {
            Brand b1 = brandService.createBrand("나이키");
            brandService.createBrand("아디다스");

            CoreException result = assertThrows(CoreException.class,
                () -> brandService.updateBrand(b1.getId(), "아디다스"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("같은 이름으로 수정하면, 정상 처리된다.")
        @Test
        void updatesBrand_whenNameIsSame() {
            Brand saved = brandService.createBrand("나이키");

            Brand updated = brandService.updateBrand(saved.getId(), "나이키");

            assertThat(updated.getName()).isEqualTo("나이키");
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            CoreException result = assertThrows(CoreException.class,
                () -> brandService.updateBrand(999L, "나이키"));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class DeleteBrand {

        @DisplayName("존재하는 브랜드면, 이후 조회 시 NOT_FOUND가 발생한다.")
        @Test
        void deletesBrand_thenNotFoundOnGet() {
            Brand saved = brandService.createBrand("나이키");
            brandService.deleteBrand(saved.getId());

            CoreException result = assertThrows(CoreException.class,
                () -> brandService.getBrand(saved.getId()));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            CoreException result = assertThrows(CoreException.class,
                () -> brandService.deleteBrand(999L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    static class FakeBrandRepository implements BrandRepository {
        private final Map<Long, Brand> store = new HashMap<>();
        private final AtomicLong idSequence = new AtomicLong(1);

        @Override
        public Brand save(Brand brand) {
            if (brand.getId() == 0L) {
                ReflectionTestUtils.setField(brand, "id", idSequence.getAndIncrement());
            }
            store.put(brand.getId(), brand);
            return brand;
        }

        @Override
        public Optional<Brand> findById(Long id) {
            return Optional.ofNullable(store.get(id))
                .filter(b -> b.getDeletedAt() == null);
        }

        @Override
        public Optional<Brand> findByName(String name) {
            return store.values().stream()
                .filter(b -> b.getDeletedAt() == null && b.getName().equals(name))
                .findFirst();
        }

        @Override
        public List<Brand> findAll() {
            return store.values().stream()
                .filter(b -> b.getDeletedAt() == null)
                .toList();
        }

        @Override
        public boolean existsByName(String name) {
            return store.values().stream()
                .anyMatch(b -> b.getDeletedAt() == null && b.getName().equals(name));
        }
    }
}
