package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class BrandFacadeIntegrationTest {

    @Autowired
    private BrandFacade brandFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandModel saveBrand(String name) {
        return brandRepository.save(new BrandModel(name));
    }

    private ProductModel saveProduct(Long brandId, String name, BigDecimal price) {
        return productRepository.save(new ProductModel(brandId, name, price));
    }

    private void saveStock(Long productId, Long quantity) {
        stockRepository.save(new StockModel(productId, quantity));
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class DeleteBrand {

        @DisplayName("브랜드와 관련 상품이 있으면 브랜드·상품·재고가 모두 소프트 삭제된다.")
        @Test
        void deletesBrandAndRelatedProducts_whenBrandHasProducts() {
            // given
            BrandModel brand = saveBrand("Nike");
            ProductModel product1 = saveProduct(brand.getId(), "에어맥스", BigDecimal.valueOf(150000));
            ProductModel product2 = saveProduct(brand.getId(), "조던", BigDecimal.valueOf(200000));
            saveStock(product1.getId(), 10L);
            saveStock(product2.getId(), 5L);

            // when
            brandFacade.deleteBrand(brand.getId());

            // then
            assertAll(
                () -> assertThat(brandRepository.findById(brand.getId())).isEmpty(),
                () -> assertThat(productRepository.find(product1.getId())).isEmpty(),
                () -> assertThat(productRepository.find(product2.getId())).isEmpty(),
                () -> assertThat(stockRepository.findByProductId(product1.getId())).isEmpty(),
                () -> assertThat(stockRepository.findByProductId(product2.getId())).isEmpty()
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandDoesNotExist() {
            // when
            CoreException result = assertThrows(CoreException.class,
                () -> brandFacade.deleteBrand(999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("브랜드에 상품이 없으면 브랜드만 소프트 삭제된다.")
        @Test
        void deletesBrandOnly_whenBrandHasNoProducts() {
            // given
            BrandModel brand = saveBrand("Adidas");

            // when
            assertDoesNotThrow(() -> brandFacade.deleteBrand(brand.getId()));

            // then
            assertThat(brandRepository.findById(brand.getId())).isEmpty();
        }
    }
}
