package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductRepositoryTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private BrandModel brand;

    @BeforeEach
    void setUp() {
        brand = brandRepository.save(new BrandModel("테스트브랜드"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 브랜드 내 동일한 이름으로 저장하면, DataIntegrityViolationException이 발생한다.")
    @Test
    void throwsException_whenDuplicateBrandIdAndNameIsInserted() {
        productRepository.save(new ProductModel(brand.getId(), new ProductName("테스트상품")));

        assertThrows(DataIntegrityViolationException.class, () ->
                productRepository.save(new ProductModel(brand.getId(), new ProductName("테스트상품")))
        );
    }
}
