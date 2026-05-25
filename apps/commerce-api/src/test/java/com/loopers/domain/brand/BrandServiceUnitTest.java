package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandServiceUnitTest {

    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(new FakeBrandRepository());
    }

    static class FakeBrandRepository implements BrandRepository {
        private final Map<Long, Brand> store = new HashMap<>();
        private final AtomicLong idSequence = new AtomicLong(1);

        @Override
        public Brand save(Brand brand) {
            if (brand.getId() == null) {
                Brand persisted = new Brand(
                    idSequence.getAndIncrement(),
                    brand.getName(),
                    brand.getDescription(),
                    ZonedDateTime.now(),
                    ZonedDateTime.now(),
                    brand.getDeletedAt()
                );
                store.put(persisted.getId(), persisted);
                return persisted;
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
        public Page<Brand> findAll(Pageable pageable) {
            List<Brand> active = store.values().stream()
                .filter(b -> b.getDeletedAt() == null)
                .toList();
            return new PageImpl<>(active, pageable, active.size());
        }
    }

    @DisplayName("브랜드를 단건 조회할 때,")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 ID를 주면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenValidIdIsProvided() {
            Brand created = brandService.createBrand("무신사", "무신사 브랜드");

            Brand result = brandService.getBrand(created.getId());

            assertAll(
                () -> assertThat(result.getId()).isEqualTo(created.getId()),
                () -> assertThat(result.getName()).isEqualTo("무신사")
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
            Brand created = brandService.createBrand("삭제 브랜드", "설명");
            brandService.deleteBrand(created.getId());

            CoreException ex = assertThrows(CoreException.class,
                () -> brandService.getBrand(created.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 목록을 조회할 때,")
    @Nested
    class GetAllBrands {

        @DisplayName("삭제된 브랜드는 제외하고 조회한다.")
        @Test
        void excludesDeletedBrands() {
            brandService.createBrand("활성 브랜드", "설명");
            Brand toDelete = brandService.createBrand("삭제 브랜드", "설명");
            brandService.deleteBrand(toDelete.getId());

            Page<Brand> result = brandService.getAllBrands(Pageable.ofSize(20));

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
            Brand created = brandService.createBrand("원래", "원래 설명");

            Brand updated = brandService.updateBrand(created.getId(), "새 이름", "새 설명");

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
