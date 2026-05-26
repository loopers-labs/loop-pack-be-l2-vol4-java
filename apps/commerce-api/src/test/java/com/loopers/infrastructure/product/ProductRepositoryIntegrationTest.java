package com.loopers.infrastructure.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class ProductRepositoryIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel createProduct(String name) {
        return ProductModel.builder()
            .brandId(1L)
            .rawName(name)
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(50)
            .build();
    }

    @DisplayName("상품을 저장할 때,")
    @Nested
    class Save {

        @DisplayName("저장하면 식별자가 부여되고 모든 필드가 보존된 채 조회된다.")
        @Test
        void assignsId_andPreservesFields() {
            // arrange & act
            ProductModel savedProduct = productRepository.save(createProduct("감성 가디건"));

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(savedProduct.getId()).orElseThrow();
            assertAll(
                () -> assertThat(savedProduct.getId()).isNotNull(),
                () -> assertThat(reloadedProduct.getBrandId()).isEqualTo(1L),
                () -> assertThat(reloadedProduct.getName().value()).isEqualTo("감성 가디건"),
                () -> assertThat(reloadedProduct.getDescription()).isEqualTo("포근한 감성 가디건"),
                () -> assertThat(reloadedProduct.getPrice().value()).isEqualTo(39_000),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(50)
            );
        }
    }
}
