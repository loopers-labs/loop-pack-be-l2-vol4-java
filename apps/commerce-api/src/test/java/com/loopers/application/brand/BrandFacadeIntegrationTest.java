package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BrandFacadeIntegrationTest {

    @Autowired private BrandFacade brandFacade;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드 삭제 시,")
    @Nested
    class DeleteBrand {

        @DisplayName("해당 브랜드의 상품이 판매 중단 처리된다.")
        @Test
        void suspendsProducts_whenBrandIsDeleted() {
            BrandModel brand = brandRepository.save(new BrandModel("나이키"));
            productRepository.save(new ProductModel(brand.getId(), new ProductName("상품A")));
            productRepository.save(new ProductModel(brand.getId(), new ProductName("상품B")));

            brandFacade.delete(brand.getId());

            List<ProductModel> products = productRepository.findAllByBrandId(brand.getId());
            assertThat(products).allMatch(p -> p.getStatus() == ProductStatus.INACTIVE);
        }
    }
}
