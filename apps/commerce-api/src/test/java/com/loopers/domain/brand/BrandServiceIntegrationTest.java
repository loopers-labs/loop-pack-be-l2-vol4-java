package com.loopers.domain.brand;

import com.loopers.domain.brand.enums.BrandStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.enums.ProductStatus;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BrandServiceIntegrationTest {

    @Autowired private BrandService brandService;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String DEFAULT_NAME = "나이키";
    private static final String OTHER_NAME = "아디다스";

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel saveDefaultBrand() {
        return brandRepository.save(new BrandModel(DEFAULT_NAME));
    }

    @DisplayName("브랜드 생성 시,")
    @Nested
    class Create {

        @DisplayName("유효한 이름이면, 브랜드가 저장된다.")
        @Test
        void savesBrand_whenNameIsValid() {
            BrandModel result = brandService.create(DEFAULT_NAME);

            assertThat(result.getName()).isEqualTo(DEFAULT_NAME);
            assertThat(result.getStatus()).isEqualTo(BrandStatus.ACTIVE);
        }
    }

    @DisplayName("브랜드 조회 시,")
    @Nested
    class Get {

        @DisplayName("브랜드가 존재하면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandExists() {
            BrandModel saved = saveDefaultBrand();

            BrandModel result = brandService.get(saved.getId());

            assertThat(result.getName()).isEqualTo(DEFAULT_NAME);
        }

    }

    @DisplayName("브랜드 수정 시,")
    @Nested
    class Update {

        @DisplayName("유효한 이름이면, 브랜드명이 변경된다.")
        @Test
        void updatesBrandName_whenNameIsValid() {
            BrandModel saved = saveDefaultBrand();

            BrandModel result = brandService.update(saved.getId(), OTHER_NAME);

            assertThat(result.getName()).isEqualTo(OTHER_NAME);
        }
    }

    @DisplayName("브랜드 삭제 시,")
    @Nested
    class Delete {

        @DisplayName("브랜드가 비활성화되고, 목록에서 제외된다.")
        @Test
        void deactivatesAndExcludesFromList_whenBrandExists() {
            BrandModel saved = saveDefaultBrand();

            brandService.delete(saved.getId());

            BrandModel deleted = brandRepository.findById(saved.getId()).get();
            assertThat(deleted.getStatus()).isEqualTo(BrandStatus.INACTIVE);
            assertThat(deleted.getDeletedAt()).isNotNull();

            Page<BrandModel> list = brandService.getList(PageRequest.of(0, 10));
            assertThat(list.getContent()).isEmpty();
        }

        @DisplayName("브랜드 삭제 시, 해당 브랜드의 상품이 판매 중단 처리된다.")
        @Test
        void suspendsProducts_whenBrandIsDeleted() {
            BrandModel brand = saveDefaultBrand();
            productRepository.save(new ProductModel(brand.getId(), new ProductName("상품A")));
            productRepository.save(new ProductModel(brand.getId(), new ProductName("상품B")));

            brandService.delete(brand.getId());

            List<ProductModel> products = productRepository.findAllByBrandId(brand.getId());
            assertThat(products).allMatch(p -> p.getStatus() == ProductStatus.INACTIVE);
        }

    }

    @DisplayName("브랜드 목록 조회 시,")
    @Nested
    class GetList {

        @DisplayName("삭제되지 않은 브랜드만 반환된다.")
        @Test
        void returnsOnlyActiveBrands() {
            BrandModel active = brandService.create(DEFAULT_NAME);
            BrandModel deleted = brandService.create(OTHER_NAME);
            brandService.delete(deleted.getId());

            Page<BrandModel> result = brandService.getList(PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(active.getId());
        }
    }
}
