package com.loopers.domain.brand;

import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class BrandServiceTest {

    @DisplayName("브랜드를 생성할 때 ")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 이름과 설명이 주어지면, 생성된 브랜드를 저장한다.")
        @Test
        void savesCreatedBrand_whenNameAndDescriptionAreValid() {
            // arrange
            FakeBrandRepository brandRepository = new FakeBrandRepository();
            BrandService brandService = new BrandService(brandRepository);
            String name = "애플";
            String description = "기술과 디자인으로 일상을 새롭게 만드는 브랜드";

            // act
            Brand created = brandService.createBrand(name, description);

            // assert
            assertAll(
                () -> assertThat(created.getName().value()).isEqualTo(name),
                () -> assertThat(created.getDescription()).isEqualTo(description),
                () -> assertThat(brandRepository.savedBrand).isSameAs(created)
            );
        }
    }

    private static class FakeBrandRepository implements BrandRepository {
        private Brand savedBrand;

        @Override
        public Brand save(Brand brand) {
            this.savedBrand = brand;
            return brand;
        }

        @Override
        public Optional<Brand> findActiveById(Long brandId) {
            return Optional.ofNullable(savedBrand);
        }

        @Override
        public List<Brand> findActiveAllByIds(Collection<Long> brandIds) {
            return savedBrand == null ? List.of() : List.of(savedBrand);
        }

        @Override
        public PageResult<Brand> findActiveAll(PageQuery query) {
            List<Brand> brands = savedBrand == null ? List.of() : List.of(savedBrand);
            return new PageResult<>(
                brands,
                brands.size(),
                brands.isEmpty() ? 0 : 1,
                query.page(),
                query.size(),
                query.page() == 0,
                true
            );
        }
    }
}
