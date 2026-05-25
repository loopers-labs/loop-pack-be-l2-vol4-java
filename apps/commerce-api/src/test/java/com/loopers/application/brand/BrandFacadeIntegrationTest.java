package com.loopers.application.brand;

import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class BrandFacadeIntegrationTest {

    @Autowired
    private BrandFacade brandFacade;

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

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class DeleteBrand {

        @DisplayName("브랜드를 삭제하면 소속 상품도 삭제된다.")
        @Test
        void deletesBrandAndCascadesToProducts() {
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity product = productJpaRepository.save(
                new ProductEntity(brand.getId(), "상품", BigDecimal.valueOf(10000))
            );

            brandFacade.deleteBrand(brand.getId());

            BrandEntity deletedBrand = brandJpaRepository.findById(brand.getId()).orElseThrow();
            ProductEntity deletedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(deletedBrand.getDeletedAt()).isNotNull(),
                () -> assertThat(deletedProduct.getDeletedAt()).isNotNull()
            );
        }
    }
}
