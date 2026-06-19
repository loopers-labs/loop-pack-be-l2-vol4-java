package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Transactional
@SpringBootTest
class ProductRepositoryIntegrationTest extends ProductRepositoryContractTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager em;

    @Override
    ProductRepository repository() {
        return productRepository;
    }

    @DisplayName("brandId에 해당하는 상품을 일괄 소프트 삭제할 때, 해당 브랜드의 상품들이 조회되지 않는다.")
    @Test
    void softDeleteAllByBrandId_softDeletesProductsUnderBrand() {
        // given
        Long brandId = 10L;
        ProductModel product1 = productRepository.save(new ProductModel(brandId, "상품A", BigDecimal.valueOf(10000)));
        ProductModel product2 = productRepository.save(new ProductModel(brandId, "상품B", BigDecimal.valueOf(20000)));

        // when
        productRepository.softDeleteAllByBrandId(brandId, ZonedDateTime.now());
        em.clear();

        // then
        assertAll(
            () -> assertThat(productRepository.find(product1.getId())).isEmpty(),
            () -> assertThat(productRepository.find(product2.getId())).isEmpty()
        );
    }

    @DisplayName("brandId에 해당하는 상품을 일괄 소프트 삭제할 때, 다른 브랜드의 상품은 영향을 받지 않는다.")
    @Test
    void softDeleteAllByBrandId_doesNotAffectOtherBrandProducts() {
        // given
        Long targetBrandId = 11L;
        Long otherBrandId = 12L;
        ProductModel targetProduct = productRepository.save(new ProductModel(targetBrandId, "타겟상품", BigDecimal.valueOf(10000)));
        ProductModel otherProduct = productRepository.save(new ProductModel(otherBrandId, "다른상품", BigDecimal.valueOf(20000)));

        // when
        productRepository.softDeleteAllByBrandId(targetBrandId, ZonedDateTime.now());
        em.clear();

        // then
        assertAll(
            () -> assertThat(productRepository.find(targetProduct.getId())).isEmpty(),
            () -> assertThat(productRepository.find(otherProduct.getId())).isPresent()
        );
    }
}
