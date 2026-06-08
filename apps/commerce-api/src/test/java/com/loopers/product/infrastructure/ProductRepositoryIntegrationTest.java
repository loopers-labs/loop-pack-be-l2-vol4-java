package com.loopers.product.infrastructure;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductSortOption;
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
    private final LikeRepository likeRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductRepositoryIntegrationTest(
            ProductRepository productRepository,
            LikeRepository likeRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.productRepository = productRepository;
        this.likeRepository = likeRepository;
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

        List<Product> result = productRepository.findAllOnSale(ProductSortOption.LATEST);

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

        List<Product> result = productRepository.findAllOnSale(ProductSortOption.PRICE_ASC);

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
    @DisplayName("findAllOnSale(LIKES_DESC) 는 활성 좋아요 수 내림차순 정렬하며, 취소 좋아요·판매중지는 제외한다")
    void givenProductsWithLikes_whenFindAllOnSaleLikesDesc_thenOrdersByActiveLikeCount() {
        Product many = save("많이", 1000L);
        Product few = save("적게", 2000L);
        save("없음", 3000L);
        Product suspended = saveSuspended("판매중지", 4000L);

        likeRepository.save(Like.create(1L, many.getId()));
        likeRepository.save(Like.create(2L, many.getId()));
        likeRepository.save(Like.create(1L, few.getId()));
        Like cancelled = Like.create(2L, few.getId());
        cancelled.delete();
        likeRepository.save(cancelled);
        likeRepository.save(Like.create(1L, suspended.getId()));
        likeRepository.save(Like.create(2L, suspended.getId()));
        likeRepository.save(Like.create(3L, suspended.getId()));

        List<Product> result = productRepository.findAllOnSale(ProductSortOption.LIKES_DESC);

        assertThat(result)
                .extracting(Product::getName)
                .containsExactly("많이", "적게", "없음");
    }
}
