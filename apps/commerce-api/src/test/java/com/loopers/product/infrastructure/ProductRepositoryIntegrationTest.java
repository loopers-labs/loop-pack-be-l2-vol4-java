package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductSortOption;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductRepositoryIntegrationTest {

    private static final Long BRAND_ID = 1L;

    private final ProductRepository productRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductRepositoryIntegrationTest(
            ProductRepository productRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.productRepository = productRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    private Product saveWithLikeCount(String name, long price, long likeCount) {
        Product product = Product.create(BRAND_ID, name, "설명", price, null);
        ReflectionTestUtils.setField(product, "likeCount", likeCount);
        return productRepository.save(product);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product save(String name, long price) {
        return productRepository.save(Product.create(BRAND_ID, name, "설명", price, null));
    }

    private Product saveForBrand(Long brandId, String name, long price) {
        return productRepository.save(Product.create(brandId, name, "설명", price, null));
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
        assertThat(found.get().getPrice().value()).isEqualTo(29_000L);
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
    @DisplayName("findAllOnSale(LATEST) 는 판매중 상품만 최신순으로 반환한다 (판매중지·삭제 제외)")
    void givenMixedProducts_whenFindAllOnSaleLatest_thenReturnsOnlyOnSale() throws Exception {
        save("A", 1000L);
        Thread.sleep(10);
        save("B", 2000L);
        Thread.sleep(10);
        saveSuspended("판매중지", 3000L);

        List<Product> result = productRepository.findAllOnSale(null, ProductSortOption.LATEST, 0, 100);

        assertThat(result)
                .extracting(Product::getName)
                .containsExactly("B", "A");
    }

    @Test
    @DisplayName("findAllOnSale(PRICE_ASC) 는 판매중 상품만 가격 오름차순으로 반환한다")
    void givenMixedProducts_whenFindAllOnSalePriceAsc_thenReturnsOnlyOnSaleInPriceAsc() {
        save("비싼것", 50_000L);
        save("싼것", 10_000L);
        saveSuspended("판매중지", 1L);

        List<Product> result = productRepository.findAllOnSale(null, ProductSortOption.PRICE_ASC, 0, 100);

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

    @Test
    @DisplayName("findAllOnSale(LIKES_DESC) 는 비정규화된 like_count 내림차순으로 정렬하며 판매중지는 제외한다")
    void givenProductsWithLikeCount_whenFindAllOnSaleLikesDesc_thenOrdersByLikeCount() {
        saveWithLikeCount("많이", 1000L, 5);
        saveWithLikeCount("적게", 2000L, 2);
        saveWithLikeCount("없음", 3000L, 0);
        Product suspended = saveSuspended("판매중지", 4000L);
        ReflectionTestUtils.setField(suspended, "likeCount", 100L);
        productRepository.save(suspended);

        List<Product> result = productRepository.findAllOnSale(null, ProductSortOption.LIKES_DESC, 0, 100);

        assertThat(result)
                .extracting(Product::getName)
                .containsExactly("많이", "적게", "없음");
    }

    @Test
    @DisplayName("findAllOnSale(brandId) 는 brandId 가 주어지면 해당 브랜드의 판매중 상품만 반환한다")
    void givenBrandFilter_whenFindAllOnSale_thenReturnsOnlyThatBrand() {
        saveForBrand(1L, "브랜드1-A", 1000L);
        saveForBrand(1L, "브랜드1-B", 2000L);
        saveForBrand(2L, "브랜드2-A", 3000L);

        List<Product> result = productRepository.findAllOnSale(1L, ProductSortOption.LATEST, 0, 10);

        assertThat(result)
                .extracting(Product::getBrandId)
                .containsOnly(1L);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findAllOnSale(brandId=null) 은 전체 브랜드의 판매중 상품을 반환한다")
    void givenNoBrandFilter_whenFindAllOnSale_thenReturnsAllBrands() {
        saveForBrand(1L, "브랜드1", 1000L);
        saveForBrand(2L, "브랜드2", 2000L);

        List<Product> result = productRepository.findAllOnSale(null, ProductSortOption.LATEST, 0, 10);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findAllOnSale 은 offset/limit 으로 페이지 슬라이스만 반환한다")
    void givenOffsetLimit_whenFindAllOnSale_thenReturnsPageSlice() {
        saveForBrand(1L, "p1", 1000L);
        saveForBrand(1L, "p2", 2000L);
        saveForBrand(1L, "p3", 3000L);
        saveForBrand(1L, "p4", 4000L);
        saveForBrand(1L, "p5", 5000L);

        List<Product> page2 = productRepository.findAllOnSale(1L, ProductSortOption.PRICE_ASC, 2, 2);

        assertThat(page2)
                .extracting(Product::getName)
                .containsExactly("p3", "p4");
    }

    @Test
    @DisplayName("countOnSale(brandId) 은 해당 브랜드의 판매중 상품 수만 센다(판매중지·삭제·타브랜드 제외)")
    void givenBrand_whenCountOnSale_thenCountsOnlyOnSale() {
        saveForBrand(1L, "온세일1", 1000L);
        saveForBrand(1L, "온세일2", 2000L);
        Product suspended = Product.create(1L, "중지", "설명", 3000L, null);
        suspended.suspend();
        productRepository.save(suspended);
        saveForBrand(2L, "다른브랜드", 4000L);

        long count = productRepository.countOnSale(1L);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("countOnSale(brandId=null) 은 전체 판매중 상품 수를 센다")
    void givenNoBrand_whenCountOnSale_thenCountsAllOnSale() {
        saveForBrand(1L, "A", 1000L);
        saveForBrand(2L, "B", 2000L);
        saveForBrand(3L, "C", 3000L);

        long count = productRepository.countOnSale(null);

        assertThat(count).isEqualTo(3L);
    }
}
