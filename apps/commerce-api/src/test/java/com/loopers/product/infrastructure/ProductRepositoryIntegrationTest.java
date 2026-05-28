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

    private Product save(String name, long price) {
        return productRepository.save(Product.create(BRAND_ID, name, "설명", price, null));
    }

    private Product saveSuspended(String name, long price) {
        Product product = Product.create(BRAND_ID, name, "설명", price, null);
        product.suspend();
        return productRepository.save(product);
    }

    @Test
    @DisplayName("save 후 findById 로 같은 상품을 조회할 수 있다")
    void givenSavedProduct_whenFindById_thenReturnsProduct() {
        Product saved = save("셔츠", 29_000L);

        Optional<Product> found = productRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("셔츠");
        assertThat(found.get().getPrice()).isEqualTo(29_000L);
    }

    @Test
    @DisplayName("soft-delete 된 상품은 findById 결과에서 제외된다")
    void givenSoftDeletedProduct_whenFindById_thenReturnsEmpty() {
        Product saved = save("셔츠", 29_000L);
        saved.delete();
        productRepository.save(saved);

        assertThat(productRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("findActiveById 는 판매중 상품만 반환하고 판매중지 상품은 제외한다")
    void givenSuspendedProduct_whenFindActiveById_thenReturnsEmpty() {
        Product onSale = save("판매중", 1000L);
        Product suspended = saveSuspended("판매중지", 2000L);

        assertThat(productRepository.findActiveById(onSale.getId())).isPresent();
        assertThat(productRepository.findActiveById(suspended.getId())).isEmpty();
    }

    @Test
    @DisplayName("findAllOnSaleOrderByLatest 는 판매중 상품만 최신순으로 반환한다 (판매중지·삭제 제외)")
    void givenMixedProducts_whenFindAllOnSaleOrderByLatest_thenReturnsOnlyOnSale() throws Exception {
        save("A", 1000L);
        Thread.sleep(10);
        save("B", 2000L);
        Thread.sleep(10);
        saveSuspended("판매중지", 3000L);

        List<Product> result = productRepository.findAllOnSaleOrderByLatest();

        assertThat(result)
                .extracting(Product::getName)
                .containsExactly("B", "A");
    }

    @Test
    @DisplayName("findAllOnSaleOrderByPriceAsc 는 판매중 상품만 가격 오름차순으로 반환한다")
    void givenMixedProducts_whenFindAllOnSaleOrderByPriceAsc_thenReturnsOnlyOnSaleInPriceAsc() {
        save("비싼것", 50_000L);
        save("싼것", 10_000L);
        saveSuspended("판매중지", 1L);

        List<Product> result = productRepository.findAllOnSaleOrderByPriceAsc();

        assertThat(result)
                .extracting(Product::getName)
                .containsExactly("싼것", "비싼것");
    }

    @Test
    @DisplayName("findAllOrderByLatest(관리자) 는 판매중지 상품도 포함하고 삭제만 제외한다")
    void givenMixedProducts_whenFindAllOrderByLatest_thenIncludesSuspendedExcludesDeleted() {
        save("판매중", 1000L);
        saveSuspended("판매중지", 2000L);
        Product deleted = save("삭제됨", 3000L);
        deleted.delete();
        productRepository.save(deleted);

        List<Product> result = productRepository.findAllOrderByLatest();

        assertThat(result)
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("판매중", "판매중지");
    }
}
