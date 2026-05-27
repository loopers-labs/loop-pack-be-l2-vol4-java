package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
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
class ProductRepositoryIntegrationTest {

    private static final Long BRAND_ID = 1L;

    private final ProductRepository productRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductRepositoryIntegrationTest(ProductRepository productRepository, DatabaseCleanUp databaseCleanUp) {
        this.productRepository = productRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("save 후 findById 로 같은 상품을 조회할 수 있다")
    void givenSavedProduct_whenFindById_thenReturnsProduct() {
        Product saved = productRepository.save(Product.create(BRAND_ID, "셔츠", "설명", 29_000L));

        Optional<Product> found = productRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("셔츠");
        assertThat(found.get().getPrice()).isEqualTo(29_000L);
    }

    @Test
    @DisplayName("존재하지 않는 id 로 findById 하면 빈 값을 반환한다")
    void givenNonExistingId_whenFindById_thenReturnsEmpty() {
        Optional<Product> found = productRepository.findById(999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("soft-delete 된 상품은 findById 결과에서 제외된다")
    void givenSoftDeletedProduct_whenFindById_thenReturnsEmpty() {
        Product saved = productRepository.save(Product.create(BRAND_ID, "셔츠", "설명", 29_000L));
        saved.delete();
        productRepository.save(saved);

        Optional<Product> found = productRepository.findById(saved.getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findAllOrderByLatest 는 최근 생성 순으로 반환하며 soft-delete 는 제외한다")
    void givenMultipleProducts_whenFindAllOrderByLatest_thenReturnsOnlyActiveInLatestOrder() throws Exception {
        Product a = productRepository.save(Product.create(BRAND_ID, "A", "설명", 1000L));
        Thread.sleep(10); // createdAt 명확한 순서 확보
        Product b = productRepository.save(Product.create(BRAND_ID, "B", "설명", 2000L));
        Thread.sleep(10);
        Product c = productRepository.save(Product.create(BRAND_ID, "C", "설명", 3000L));
        c.delete();
        productRepository.save(c);

        List<Product> result = productRepository.findAllOrderByLatest();

        assertThat(result)
                .extracting(Product::getName)
                .containsExactly("B", "A");
    }

    @Test
    @DisplayName("findAllOrderByPriceAsc 는 가격 오름차순으로 반환하며 soft-delete 는 제외한다")
    void givenMultipleProducts_whenFindAllOrderByPriceAsc_thenReturnsOnlyActiveInPriceAscOrder() {
        productRepository.save(Product.create(BRAND_ID, "비싼것", "설명", 50_000L));
        productRepository.save(Product.create(BRAND_ID, "중간", "설명", 30_000L));
        productRepository.save(Product.create(BRAND_ID, "싼것", "설명", 10_000L));
        Product deleted = productRepository.save(Product.create(BRAND_ID, "삭제됨", "설명", 0L));
        deleted.delete();
        productRepository.save(deleted);

        List<Product> result = productRepository.findAllOrderByPriceAsc();

        assertThat(result)
                .extracting(Product::getName)
                .containsExactly("싼것", "중간", "비싼것");
    }
}
