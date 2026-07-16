package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductBatchQueryIntegrationTest {

    private final ProductRepository productRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductBatchQueryIntegrationTest(
        ProductRepository productRepository,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.productRepository = productRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel save(String name) {
        return productJpaRepository.save(new ProductModel(name, name + " 설명", 10_000L, 1L));
    }

    @DisplayName("주어진 id 들에 해당하는 상품을 한 번에 조회한다.")
    @Test
    void returnsProductsMatchingGivenIds() {
        // given
        ProductModel a = save("상품A");
        ProductModel b = save("상품B");

        // when
        List<ProductModel> found = productRepository.findAllActiveByIds(List.of(a.getId(), b.getId()));

        // then
        assertThat(found).extracting(ProductModel::getId)
            .containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @DisplayName("삭제된 상품은 배치 조회 결과에서 제외된다.")
    @Test
    void excludesDeletedProducts() {
        // given
        ProductModel active = save("살아있는상품");
        ProductModel deleted = save("삭제된상품");
        deleted.delete();
        productJpaRepository.save(deleted);

        // when
        List<ProductModel> found = productRepository.findAllActiveByIds(List.of(active.getId(), deleted.getId()));

        // then
        assertThat(found).extracting(ProductModel::getId).containsExactly(active.getId());
    }

    @DisplayName("존재하지 않는 id 는 결과에서 조용히 빠진다.")
    @Test
    void ignoresUnknownIds() {
        // given
        ProductModel a = save("상품A");

        // when
        List<ProductModel> found = productRepository.findAllActiveByIds(List.of(a.getId(), 999_999L));

        // then
        assertThat(found).extracting(ProductModel::getId).containsExactly(a.getId());
    }
}
