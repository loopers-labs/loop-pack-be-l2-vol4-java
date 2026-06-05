package com.loopers.application.product;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductApplicationServiceIntegrationTest {

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandAId;
    private Long brandBId;

    @BeforeEach
    void setUp() {
        brandAId = brandJpaRepository.save(Brand.create("브랜드A", "소개A")).getId();
        brandBId = brandJpaRepository.save(Brand.create("브랜드B", "소개B")).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("register 는 ")
    @Nested
    class Register {

        @DisplayName("브랜드가 존재하면 상품을 INSERT 하고 Created 정보를 돌려준다.")
        @Test
        void persistsProduct_whenBrandExists() {
            ProductInfo.Created result = productApplicationService.register(
                    new ProductCriteria.Register(brandAId, "상품1", 1_000L, 10));

            assertThat(result.id()).isNotNull();
            assertThat(result.brandId()).isEqualTo(brandAId);
            assertThat(result.name()).isEqualTo("상품1");
            assertThat(result.price()).isEqualTo(1_000L);
            assertThat(result.stock()).isEqualTo(10);
            assertThat(productJpaRepository.findById(result.id())).isPresent();
        }

        @DisplayName("존재하지 않는 브랜드면 NOT_FOUND. (AC-14-2)")
        @Test
        void throwsNotFound_whenBrandMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> productApplicationService.register(
                            new ProductCriteria.Register(99999L, "상품1", 1_000L, 10)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getProduct 는 ")
    @Nested
    class GetProduct {

        @DisplayName("상품·브랜드·좋아요 수를 합성해 Detail 을 돌려준다. (AC-03-1)")
        @Test
        void returnsDetail_withBrandAndLikeCount() {
            Product p = productJpaRepository.save(
                    Product.create(brandAId, "상품1", Money.of(1_000L), Stock.of(10)));
            likeJpaRepository.save(Like.create(100L, p.getId()));
            likeJpaRepository.save(Like.create(101L, p.getId()));

            ProductInfo.Detail result = productApplicationService.getProduct(p.getId());

            assertThat(result.id()).isEqualTo(p.getId());
            assertThat(result.brandId()).isEqualTo(brandAId);
            assertThat(result.brandName()).isEqualTo("브랜드A");
            assertThat(result.likeCount()).isEqualTo(2L);
            assertThat(result.soldOut()).isFalse();
        }

        @DisplayName("논리 삭제된 상품을 조회하면 NOT_FOUND. (AC-03-2)")
        @Test
        void throwsNotFound_whenDeleted() {
            Product p = productJpaRepository.save(
                    Product.create(brandAId, "상품1", Money.of(1_000L), Stock.of(10)));
            productApplicationService.delete(p.getId());

            CoreException result = assertThrows(CoreException.class,
                    () -> productApplicationService.getProduct(p.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고 0 이면 soldOut=true.")
        @Test
        void returnsSoldOut_whenStockZero() {
            Product p = productJpaRepository.save(
                    Product.create(brandAId, "상품1", Money.of(1_000L), Stock.of(0)));

            ProductInfo.Detail result = productApplicationService.getProduct(p.getId());

            assertThat(result.soldOut()).isTrue();
        }
    }

    @DisplayName("getAllProducts 는 ")
    @Nested
    class GetAllProducts {

        @DisplayName("LATEST 정렬은 최신 등록 순 (createdAt DESC, id DESC).")
        @Test
        void sortsByLatest() {
            Product p1 = productJpaRepository.save(Product.create(brandAId, "상품1", Money.of(1_000L), Stock.of(10)));
            Product p2 = productJpaRepository.save(Product.create(brandAId, "상품2", Money.of(2_000L), Stock.of(10)));
            Product p3 = productJpaRepository.save(Product.create(brandAId, "상품3", Money.of(3_000L), Stock.of(10)));

            PageResult<ProductInfo.ListItem> result = productApplicationService.getAllProducts(
                    new ProductCriteria.GetAll(0, 20, null, "LATEST"));

            assertThat(result.content()).extracting(ProductInfo.ListItem::id)
                    .containsExactly(p3.getId(), p2.getId(), p1.getId());
        }

        @DisplayName("PRICE_ASC 정렬은 가격 오름차순.")
        @Test
        void sortsByPriceAsc() {
            productJpaRepository.save(Product.create(brandAId, "비싼것", Money.of(3_000L), Stock.of(10)));
            productJpaRepository.save(Product.create(brandAId, "싼것", Money.of(1_000L), Stock.of(10)));
            productJpaRepository.save(Product.create(brandAId, "중간", Money.of(2_000L), Stock.of(10)));

            PageResult<ProductInfo.ListItem> result = productApplicationService.getAllProducts(
                    new ProductCriteria.GetAll(0, 20, null, "PRICE_ASC"));

            assertThat(result.content()).extracting(ProductInfo.ListItem::price)
                    .containsExactly(1_000L, 2_000L, 3_000L);
        }

        @DisplayName("LIKES_DESC 정렬은 좋아요 많은순.")
        @Test
        void sortsByLikesDesc() {
            Product less = productJpaRepository.save(Product.create(brandAId, "덜인기", Money.of(1_000L), Stock.of(10)));
            Product more = productJpaRepository.save(Product.create(brandAId, "인기", Money.of(1_000L), Stock.of(10)));
            likeJpaRepository.save(Like.create(100L, less.getId()));
            likeJpaRepository.save(Like.create(100L, more.getId()));
            likeJpaRepository.save(Like.create(101L, more.getId()));
            likeJpaRepository.save(Like.create(102L, more.getId()));

            PageResult<ProductInfo.ListItem> result = productApplicationService.getAllProducts(
                    new ProductCriteria.GetAll(0, 20, null, "LIKES_DESC"));

            assertThat(result.content()).extracting(ProductInfo.ListItem::id)
                    .containsExactly(more.getId(), less.getId());
        }

        @DisplayName("brandId 가 지정되면 해당 브랜드 상품만 반환한다. (AC-02-2)")
        @Test
        void filtersByBrand() {
            productJpaRepository.save(Product.create(brandAId, "A상품1", Money.of(1_000L), Stock.of(10)));
            productJpaRepository.save(Product.create(brandAId, "A상품2", Money.of(2_000L), Stock.of(10)));
            productJpaRepository.save(Product.create(brandBId, "B상품1", Money.of(1_000L), Stock.of(10)));

            PageResult<ProductInfo.ListItem> result = productApplicationService.getAllProducts(
                    new ProductCriteria.GetAll(0, 20, brandAId, "LATEST"));

            assertThat(result.content()).hasSize(2);
            assertThat(result.content()).allMatch(item -> item.brandId().equals(brandAId));
        }

        @DisplayName("페이지 사이즈에 맞춰 잘리고 hasNext 와 totalElements 를 노출한다.")
        @Test
        void paginates() {
            for (int i = 0; i < 5; i++) {
                productJpaRepository.save(Product.create(brandAId, "상품" + i, Money.of(1_000L + i), Stock.of(10)));
            }

            PageResult<ProductInfo.ListItem> page0 = productApplicationService.getAllProducts(
                    new ProductCriteria.GetAll(0, 2, null, "LATEST"));

            assertThat(page0.content()).hasSize(2);
            assertThat(page0.hasNext()).isTrue();
            assertThat(page0.totalElements()).isEqualTo(5L);
        }

        @DisplayName("논리 삭제된 상품은 목록에서 제외된다. (AC-16-1)")
        @Test
        void excludesDeletedProducts() {
            Product p1 = productJpaRepository.save(Product.create(brandAId, "활성", Money.of(1_000L), Stock.of(10)));
            Product p2 = productJpaRepository.save(Product.create(brandAId, "삭제예정", Money.of(2_000L), Stock.of(10)));
            productApplicationService.delete(p2.getId());

            PageResult<ProductInfo.ListItem> result = productApplicationService.getAllProducts(
                    new ProductCriteria.GetAll(0, 20, null, "LATEST"));

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).id()).isEqualTo(p1.getId());
        }

        @DisplayName("조건에 맞는 상품이 없으면 빈 목록을 돌려준다. (AC-02-4)")
        @Test
        void returnsEmpty_whenNoMatch() {
            PageResult<ProductInfo.ListItem> result = productApplicationService.getAllProducts(
                    new ProductCriteria.GetAll(0, 20, brandAId, "LATEST"));

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    @DisplayName("modify 는 ")
    @Nested
    class Modify {

        @DisplayName("이름과 가격을 갱신하지만 brandId 는 그대로 유지한다. (AC-15-2)")
        @Test
        void updatesNameAndPrice_keepsBrand() {
            Product saved = productJpaRepository.save(
                    Product.create(brandAId, "원본", Money.of(1_000L), Stock.of(10)));

            productApplicationService.modify(new ProductCriteria.Modify(saved.getId(), "변경", 5_000L));

            Product reloaded = productJpaRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("변경");
            assertThat(reloaded.getPrice().getAmount()).isEqualTo(5_000L);
            assertThat(reloaded.getBrandId()).isEqualTo(brandAId);
        }
    }

    @DisplayName("adjustStock 은 ")
    @Nested
    class AdjustStock {

        @DisplayName("재고 수량을 지정 값으로 설정한다. (AC-15-4)")
        @Test
        void setsAbsoluteQuantity() {
            Product saved = productJpaRepository.save(
                    Product.create(brandAId, "상품1", Money.of(1_000L), Stock.of(10)));

            productApplicationService.adjustStock(new ProductCriteria.AdjustStock(saved.getId(), 3));

            assertThat(productJpaRepository.findById(saved.getId()).orElseThrow().getStock().getQuantity()).isEqualTo(3);
        }
    }

    @DisplayName("delete 는 ")
    @Nested
    class Delete {

        @DisplayName("논리 삭제되어 deletedAt 이 채워진다.")
        @Test
        void softDeletes() {
            Product saved = productJpaRepository.save(
                    Product.create(brandAId, "상품1", Money.of(1_000L), Stock.of(10)));

            productApplicationService.delete(saved.getId());

            assertThat(productJpaRepository.findById(saved.getId()).orElseThrow().getDeletedAt()).isNotNull();
        }
    }
}
