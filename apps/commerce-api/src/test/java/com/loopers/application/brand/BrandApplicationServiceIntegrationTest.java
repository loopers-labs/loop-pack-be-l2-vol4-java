package com.loopers.application.brand;

import com.loopers.infrastructure.brand.BrandJpaEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class BrandApplicationServiceIntegrationTest {

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("register 는 ")
    @Nested
    class Register {

        @DisplayName("브랜드를 INSERT 하고 id 가 부여된 BrandInfo 를 돌려준다.")
        @Test
        void persistsBrandAndReturnsInfo() {
            BrandInfo result = brandApplicationService.register(new BrandCriteria.Register("브랜드A", "소개"));

            assertThat(result.id()).isNotNull();
            assertThat(result.name()).isEqualTo("브랜드A");
            assertThat(result.description()).isEqualTo("소개");
            assertThat(brandJpaRepository.findById(result.id())).isPresent();
        }
    }

    @DisplayName("getBrand 는 ")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드를 BrandInfo 로 돌려준다.")
        @Test
        void returnsInfo_whenBrandExists() {
            BrandJpaEntity saved = brandJpaRepository.save(BrandJpaEntity.of("브랜드A", "소개"));

            BrandInfo result = brandApplicationService.getBrand(saved.getId());

            assertThat(result.id()).isEqualTo(saved.getId());
            assertThat(result.name()).isEqualTo("브랜드A");
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 를 던진다. (AC-01-2)")
        @Test
        void throwsNotFound_whenMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> brandApplicationService.getBrand(99999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("논리 삭제된 브랜드를 조회하면 NOT_FOUND 를 던진다. (AC-01-2)")
        @Test
        void throwsNotFound_whenDeleted() {
            BrandJpaEntity saved = brandJpaRepository.save(BrandJpaEntity.of("브랜드A", "소개"));
            brandApplicationService.delete(saved.getId());

            CoreException result = assertThrows(CoreException.class,
                    () -> brandApplicationService.getBrand(saved.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getBrandList 는 ")
    @Nested
    class GetBrandList {

        @DisplayName("등록된 브랜드를 전부 돌려준다.")
        @Test
        void returnsAll() {
            brandJpaRepository.save(BrandJpaEntity.of("브랜드A", "소개"));
            brandJpaRepository.save(BrandJpaEntity.of("브랜드B", "소개"));

            List<BrandInfo> result = brandApplicationService.getBrandList();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(BrandInfo::name).containsExactlyInAnyOrder("브랜드A", "브랜드B");
        }

        @DisplayName("논리 삭제된 브랜드는 제외한다.")
        @Test
        void excludesDeleted() {
            BrandJpaEntity a = brandJpaRepository.save(BrandJpaEntity.of("브랜드A", "소개"));
            brandJpaRepository.save(BrandJpaEntity.of("브랜드B", "소개"));
            brandApplicationService.delete(a.getId());

            List<BrandInfo> result = brandApplicationService.getBrandList();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("브랜드B");
        }
    }

    @DisplayName("modify 는 ")
    @Nested
    class Modify {

        @DisplayName("이름과 설명을 갱신한다 (dirty checking).")
        @Test
        void updatesNameAndDescription() {
            BrandJpaEntity saved = brandJpaRepository.save(BrandJpaEntity.of("브랜드A", "원본 설명"));

            brandApplicationService.modify(new BrandCriteria.Modify(saved.getId(), "브랜드A2", "갱신 설명"));

            BrandJpaEntity reloaded = brandJpaRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("브랜드A2");
            assertThat(reloaded.getDescription()).isEqualTo("갱신 설명");
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 를 던진다. (AC-11-2)")
        @Test
        void throwsNotFound_whenMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> brandApplicationService.modify(new BrandCriteria.Modify(99999L, "X", "Y")));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("delete 는 ")
    @Nested
    class Delete {

        @DisplayName("브랜드를 논리 삭제하고 소속 상품도 함께 논리 삭제한다. (AC-12-1)")
        @Test
        void cascadesToProducts() {
            BrandJpaEntity brand = brandJpaRepository.save(BrandJpaEntity.of("브랜드A", "소개"));
            ProductJpaEntity p1 = productJpaRepository.save(ProductJpaEntity.of(brand.getId(), "상품1", 1_000L, 10));
            ProductJpaEntity p2 = productJpaRepository.save(ProductJpaEntity.of(brand.getId(), "상품2", 2_000L, 5));

            brandApplicationService.delete(brand.getId());

            BrandJpaEntity reloadedBrand = brandJpaRepository.findById(brand.getId()).orElseThrow();
            assertThat(reloadedBrand.getDeletedAt()).isNotNull();
            ProductJpaEntity rp1 = productJpaRepository.findById(p1.getId()).orElseThrow();
            ProductJpaEntity rp2 = productJpaRepository.findById(p2.getId()).orElseThrow();
            assertThat(rp1.getDeletedAt()).isNotNull();
            assertThat(rp2.getDeletedAt()).isNotNull();
        }

        @DisplayName("다른 브랜드의 상품은 cascade 영향을 받지 않는다.")
        @Test
        void doesNotAffectOtherBrandsProducts() {
            BrandJpaEntity targetBrand = brandJpaRepository.save(BrandJpaEntity.of("타겟", "소개"));
            BrandJpaEntity otherBrand = brandJpaRepository.save(BrandJpaEntity.of("다른", "소개"));
            ProductJpaEntity targetProduct = productJpaRepository.save(
                    ProductJpaEntity.of(targetBrand.getId(), "상품1", 1_000L, 10));
            ProductJpaEntity otherProduct = productJpaRepository.save(
                    ProductJpaEntity.of(otherBrand.getId(), "상품2", 2_000L, 5));

            brandApplicationService.delete(targetBrand.getId());

            assertThat(productJpaRepository.findById(targetProduct.getId()).orElseThrow().getDeletedAt()).isNotNull();
            assertThat(productJpaRepository.findById(otherProduct.getId()).orElseThrow().getDeletedAt()).isNull();
        }

        @DisplayName("존재하지 않으면 NOT_FOUND 를 던진다.")
        @Test
        void throwsNotFound_whenMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> brandApplicationService.delete(99999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 삭제된 브랜드를 다시 삭제하면 같은 시점이 유지된다 (도메인 delete 는 멱등).")
        @Test
        void deleteIsIdempotent() {
            BrandJpaEntity brand = brandJpaRepository.save(BrandJpaEntity.of("브랜드A", "소개"));
            brandApplicationService.delete(brand.getId());

            CoreException result = assertThrows(CoreException.class,
                    () -> brandApplicationService.delete(brand.getId()));

            // 두 번째 delete 호출은 find 단계에서 NOT_FOUND (이미 삭제된 것은 조회 안 됨)
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
