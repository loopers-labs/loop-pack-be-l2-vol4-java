package com.loopers.product.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.inventory.domain.Inventory;
import com.loopers.inventory.infrastructure.InventoryJpaRepository;
import com.loopers.like.domain.Like;
import com.loopers.like.infrastructure.LikeJpaRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductSortType;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductFacadeTest {

    @Autowired private ProductFacade productFacade;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private LikeJpaRepository likeJpaRepository;
    @Autowired private InventoryJpaRepository inventoryJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /** 상품 + 재고를 함께 시딩한다. */
    private Product saveProduct(Brand brand, String name, long price, int stock) {
        Product product = productJpaRepository.save(new Product(brand.getId(), name, "설명", price));
        inventoryJpaRepository.save(new Inventory(product.getId(), stock));
        return product;
    }

    @DisplayName("상품 생성 시 재고(Inventory)가 함께 생성되고 상세 조회에 노출된다.")
    @Test
    void createProduct_createsInventory() {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "브랜드"));

        ProductInfo created = productFacade.createProduct(brand.getId(), "운동화", "설명", 50_000L, 30);

        assertThat(created.stock()).isEqualTo(30);
        assertThat(productFacade.getProductDetail(created.id()).stock()).isEqualTo(30);
    }

    @DisplayName("상품 상세 조회 시 브랜드 정보와 좋아요 수, 재고가 함께 제공된다.")
    @Test
    void getProductDetail_combinesBrandAndLikeCount() {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "브랜드"));
        Product product = saveProduct(brand, "운동화", 50_000L, 10);
        likeJpaRepository.save(new Like(1L, product.getId()));
        likeJpaRepository.save(new Like(2L, product.getId()));

        ProductDetailInfo detail = productFacade.getProductDetail(product.getId());

        assertThat(detail.brandName()).isEqualTo("나이키");
        assertThat(detail.likeCount()).isEqualTo(2L);
        assertThat(detail.price()).isEqualTo(50_000L);
        assertThat(detail.stock()).isEqualTo(10);
    }

    @DisplayName("상품 목록을 likes_desc 로 조회하면 좋아요 수 내림차순으로 정렬된다.")
    @Test
    void getProducts_sortsByLikesDesc() {
        Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
        Product a = saveProduct(brand, "A", 1_000L, 10);
        Product b = saveProduct(brand, "B", 1_000L, 10);

        likeJpaRepository.save(new Like(1L, b.getId()));
        likeJpaRepository.save(new Like(2L, b.getId()));
        likeJpaRepository.save(new Like(3L, a.getId()));

        Page<ProductDetailInfo> result =
            productFacade.getProducts(null, ProductSortType.LIKES_DESC, 0, 20);

        assertThat(result.getContent())
            .extracting(ProductDetailInfo::id)
            .containsExactly(b.getId(), a.getId());
    }

    @DisplayName("상품 목록은 page/size 로 페이지네이션된다.")
    @Test
    void getProducts_paginates() {
        Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
        for (int i = 0; i < 5; i++) {
            saveProduct(brand, "P" + i, 1_000L, 10);
        }

        Page<ProductDetailInfo> page0 = productFacade.getProducts(null, ProductSortType.LATEST, 0, 2);
        Page<ProductDetailInfo> page2 = productFacade.getProducts(null, ProductSortType.LATEST, 2, 2);

        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.isFirst()).isTrue();
        assertThat(page0.isLast()).isFalse();

        assertThat(page2.getContent()).hasSize(1);
        assertThat(page2.isFirst()).isFalse();
        assertThat(page2.isLast()).isTrue();
    }

    @DisplayName("관리자 상품 목록은 brandId 로 필터링되고 재고를 포함한 운영 정보를 반환한다.")
    @Test
    void getProductsForAdmin_filtersByBrand() {
        Brand brandA = brandJpaRepository.save(new Brand("A", "설명"));
        Brand brandB = brandJpaRepository.save(new Brand("B", "설명"));
        saveProduct(brandA, "a1", 1_000L, 3);
        saveProduct(brandB, "b1", 1_000L, 7);

        Page<ProductInfo> result = productFacade.getProductsForAdmin(brandA.getId(), 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).brandId()).isEqualTo(brandA.getId());
        assertThat(result.getContent().get(0).stock()).isEqualTo(3);
    }

    @DisplayName("상품 수정 시 재고가 절대값으로 갱신된다.")
    @Test
    void updateProduct_setsStock() {
        Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
        Product product = saveProduct(brand, "상품", 1_000L, 10);

        ProductInfo updated = productFacade.updateProduct(product.getId(), "새상품", "새설명", 2_000L, 50);

        assertThat(updated.stock()).isEqualTo(50);
        assertThat(productFacade.getProductForAdmin(product.getId()).stock()).isEqualTo(50);
    }
}
