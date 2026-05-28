package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BrandServiceTest {

    private FakeBrandRepository brandRepository;
    private BrandService brandService;

    private static final String BRAND_NAME = "나이키";
    private static final String BRAND_DESCRIPTION = "스포츠 브랜드";

    @BeforeEach
    void setUp() {
        brandRepository = new FakeBrandRepository();
        brandService = new BrandService(brandRepository);
    }

    static class FakeBrandRepository implements BrandRepository {
        private final Map<Long, BrandEntity> store = new HashMap<>();
        private long nextId = 1L;

        @Override
        public BrandEntity save(BrandEntity brand) {
            if (brand.getId() == null) {
                long id = nextId++;
                BrandEntity saved = BrandEntity.of(id, brand.getName(), brand.getDescription(),
                        ZonedDateTime.now(), ZonedDateTime.now(), null);
                store.put(id, saved);
                return saved;
            }
            store.put(brand.getId(), brand);
            return brand;
        }

        @Override
        public Optional<BrandEntity> findById(Long id) {
            BrandEntity entity = store.get(id);
            if (entity == null || entity.getDeletedAt() != null) return Optional.empty();
            return Optional.of(BrandEntity.of(entity.getId(), entity.getName(), entity.getDescription(),
                    entity.getCreatedAt(), entity.getUpdatedAt(), entity.getDeletedAt()));
        }

        @Override
        public Page<BrandEntity> findAll(Pageable pageable) {
            List<BrandEntity> active = store.values().stream()
                    .filter(b -> b.getDeletedAt() == null)
                    .toList();
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), active.size());
            List<BrandEntity> content = start >= active.size() ? List.of() : active.subList(start, end);
            return new PageImpl<>(content, pageable, active.size());
        }

        @Override
        public Optional<BrandEntity> findByName(String name) {
            return store.values().stream()
                    .filter(b -> b.getName().equals(name) && b.getDeletedAt() == null)
                    .findFirst();
        }
    }

    @DisplayName("브랜드 생성")
    @Nested
    class Create {

        @DisplayName("[ECP] 유효한 name과 description이 주어지면 id가 할당된 브랜드가 생성된다.")
        @Test
        void createsBrand_whenRequestIsValid() {
            // act
            BrandEntity result = brandService.create(BRAND_NAME, BRAND_DESCRIPTION);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(BRAND_NAME, result.getName()),
                    () -> assertEquals(BRAND_DESCRIPTION, result.getDescription())
            );
        }

        @DisplayName("[ECP] 이미 존재하는 name이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameAlreadyExists() {
            // arrange
            brandService.create(BRAND_NAME, BRAND_DESCRIPTION);

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.create(BRAND_NAME, BRAND_DESCRIPTION));
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
        }

        @DisplayName("[Error Guessing] 삭제된 브랜드와 같은 name으로 생성하면 성공한다.")
        @Test
        void createsBrand_whenNameBelongsToDeletedBrand() {
            // arrange
            BrandEntity deleted = brandService.create(BRAND_NAME, BRAND_DESCRIPTION);
            brandService.delete(deleted.getId());

            // act
            BrandEntity result = brandService.create(BRAND_NAME, BRAND_DESCRIPTION);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(BRAND_NAME, result.getName())
            );
        }
    }

    @DisplayName("브랜드 단건 조회")
    @Nested
    class GetBrand {

        @DisplayName("[ECP] 존재하는 id로 조회하면 BrandEntity를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            // arrange
            BrandEntity created = brandService.create(BRAND_NAME, BRAND_DESCRIPTION);

            // act
            BrandEntity result = brandService.getBrand(created.getId());

            // assert
            assertAll(
                    () -> assertEquals(created.getId(), result.getId()),
                    () -> assertEquals(BRAND_NAME, result.getName())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 id로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.getBrand(999L));

            // assert
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    @DisplayName("브랜드 목록 조회")
    @Nested
    class GetBrands {

        @DisplayName("[ECP] 생성된 브랜드 수만큼 목록이 반환된다.")
        @Test
        void returnsBrandPage() {
            // arrange
            brandService.create(BRAND_NAME, BRAND_DESCRIPTION);
            brandService.create("아디다스", "독일 스포츠 브랜드");

            // act
            Page<BrandEntity> result = brandService.getBrands(PageRequest.of(0, 20));

            // assert
            assertEquals(2, result.getTotalElements());
        }
    }

    @DisplayName("브랜드 수정 — Decision Table: (브랜드 존재) × (name 중복 여부) × (동일 브랜드)")
    @Nested
    class Update {

        @DisplayName("[Decision Table] 브랜드가 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.update(999L, "아디다스", "독일 스포츠 브랜드"));

            // assert
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[Decision Table] 브랜드가 존재하고 name 중복이 없으면 수정된다.")
        @Test
        void updatesBrand_whenNameIsUnique() {
            // arrange
            BrandEntity brand = brandService.create(BRAND_NAME, BRAND_DESCRIPTION);

            // act
            brandService.update(brand.getId(), "아디다스", "독일 스포츠 브랜드");

            // assert
            BrandEntity updated = brandService.getBrand(brand.getId());
            assertAll(
                    () -> assertEquals("아디다스", updated.getName()),
                    () -> assertEquals("독일 스포츠 브랜드", updated.getDescription())
            );
        }

        @DisplayName("[Decision Table] 다른 브랜드와 name이 중복되면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNameBelongsToAnotherBrand() {
            // arrange
            brandService.create(BRAND_NAME, BRAND_DESCRIPTION);
            BrandEntity another = brandService.create("아디다스", "독일 스포츠 브랜드");

            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.update(another.getId(), BRAND_NAME, "변경된 설명"));

            // assert
            assertEquals(ErrorType.CONFLICT, exception.getErrorType());
        }

        @DisplayName("[Decision Table] 현재 브랜드와 동일한 name으로 수정하면 성공한다.")
        @Test
        void updatesBrand_whenNameIsSameAsCurrentBrand() {
            // arrange
            BrandEntity brand = brandService.create(BRAND_NAME, BRAND_DESCRIPTION);

            // act
            brandService.update(brand.getId(), BRAND_NAME, "새로운 설명");

            // assert
            BrandEntity updated = brandService.getBrand(brand.getId());
            assertEquals("새로운 설명", updated.getDescription());
        }
    }

    @DisplayName("브랜드 삭제 — State Transition: Active → Deleted")
    @Nested
    class Delete {

        @DisplayName("[State Transition] 삭제된 브랜드는 이후 조회 시 NOT_FOUND가 발생한다.")
        @Test
        void deletesBrand_thenNotFoundOnGet() {
            // arrange
            BrandEntity brand = brandService.create(BRAND_NAME, BRAND_DESCRIPTION);

            // act
            brandService.delete(brand.getId());

            // assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.getBrand(brand.getId()));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[State Transition] 존재하지 않는 id이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenBrandNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class,
                    () -> brandService.delete(999L));

            // assert
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }
}
