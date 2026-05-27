package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BrandRepositoryIntegrationTest {

    private final BrandRepository brandRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandRepositoryIntegrationTest(BrandRepository brandRepository, DatabaseCleanUp databaseCleanUp) {
        this.brandRepository = brandRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("save 후 findById 로 같은 브랜드를 조회할 수 있다")
    void givenSavedBrand_whenFindById_thenReturnsBrand() {
        Brand saved = brandRepository.save(Brand.create("루퍼스", "트렌디한 라이프스타일"));

        Optional<Brand> found = brandRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("루퍼스");
        assertThat(found.get().getDescription()).isEqualTo("트렌디한 라이프스타일");
    }

    @Test
    @DisplayName("존재하지 않는 id 로 findById 하면 빈 값을 반환한다")
    void givenNonExistingId_whenFindById_thenReturnsEmpty() {
        Optional<Brand> found = brandRepository.findById(999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("soft-delete 된 브랜드는 findById 결과에서 제외된다")
    void givenSoftDeletedBrand_whenFindById_thenReturnsEmpty() {
        Brand saved = brandRepository.save(Brand.create("루퍼스", "설명"));
        saved.delete();
        brandRepository.save(saved);

        Optional<Brand> found = brandRepository.findById(saved.getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findAll 은 활성 브랜드만 반환한다 (soft-delete 제외)")
    void givenActiveAndDeletedBrands_whenFindAll_thenReturnsOnlyActiveBrands() {
        brandRepository.save(Brand.create("활성-A", "설명"));
        brandRepository.save(Brand.create("활성-B", "설명"));
        Brand deleted = brandRepository.save(Brand.create("삭제됨", "설명"));
        deleted.delete();
        brandRepository.save(deleted);

        List<Brand> all = brandRepository.findAll();

        assertThat(all)
                .extracting(Brand::getName)
                .containsExactlyInAnyOrder("활성-A", "활성-B");
    }

    @Test
    @DisplayName("findAll 은 브랜드가 없으면 빈 리스트를 반환한다")
    void givenNoBrands_whenFindAll_thenReturnsEmptyList() {
        List<Brand> all = brandRepository.findAll();

        assertThat(all).isEmpty();
    }
}
