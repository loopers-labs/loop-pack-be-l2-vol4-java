package com.loopers.application.catalog.brand;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.pagination.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class BrandQueryServiceTest {

    @DisplayName("공개 브랜드를 조회할 때, ")
    @Nested
    class GetActiveBrand {

        @DisplayName("삭제된 브랜드는 조회하지 않는다.")
        @Test
        void throws_whenBrandIsDeleted() {
            // arrange
            FakeBrandRepository brandRepository = new FakeBrandRepository();
            Brand brand = new Brand("Loopers", "테스트 브랜드");
            brand.delete();
            brandRepository.brands.put(1L, brand);
            BrandQueryService service = new BrandQueryService(brandRepository);

            // act & assert
            assertThatThrownBy(() -> service.getActiveBrand(1L))
                .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("ADMIN 브랜드 목록을 조회할 때, ")
    @Nested
    class GetAdminBrands {

        @DisplayName("삭제된 브랜드도 목록에 포함한다.")
        @Test
        void returnsDeletedBrands() {
            // arrange
            FakeBrandRepository brandRepository = new FakeBrandRepository();
            Brand activeBrand = new Brand("Loopers", "테스트 브랜드");
            Brand deletedBrand = new Brand("Deleted", "삭제 브랜드");
            deletedBrand.delete();
            brandRepository.brands.put(1L, activeBrand);
            brandRepository.brands.put(2L, deletedBrand);
            BrandQueryService service = new BrandQueryService(brandRepository);

            // act
            PageResult<BrandResult> result = service.getAdminBrands(0, 20);

            // assert
            assertAll(
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(result.items()).extracting(BrandResult::active)
                    .containsExactly(true, false),
                () -> assertThat(result.totalElements()).isEqualTo(2)
            );
        }
    }

    private static class FakeBrandRepository implements BrandRepository {
        private final Map<Long, Brand> brands = new LinkedHashMap<>();

        @Override
        public Brand save(Brand brand) {
            return brand;
        }

        @Override
        public Optional<Brand> find(Long id) {
            return Optional.ofNullable(brands.get(id));
        }

        @Override
        public Optional<Brand> findActive(Long id) {
            return find(id).filter(Brand::isActive);
        }

        @Override
        public List<Brand> findAllByIds(Collection<Long> ids) {
            return ids.stream()
                .map(brands::get)
                .toList();
        }

        @Override
        public List<Brand> findAll(int page, int size) {
            return brands.values()
                .stream()
                .skip((long) page * size)
                .limit(size)
                .toList();
        }

        @Override
        public long countAll() {
            return brands.size();
        }
    }
}
