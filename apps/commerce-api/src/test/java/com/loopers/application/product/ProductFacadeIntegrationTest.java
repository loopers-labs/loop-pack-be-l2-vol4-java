package com.loopers.application.product;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.like.LikeFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductSortType;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductFacadeIntegrationTest {

    private final ProductFacade productFacade;
    private final BrandFacade brandFacade;
    private final LikeFacade likeFacade;
    private final ProductJpaRepository productJpaRepository;
    private final StockJpaRepository stockJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private final AtomicLong likeUserIdSeq = new AtomicLong(1L);

    private Long brandId;

    @Autowired
    public ProductFacadeIntegrationTest(
        ProductFacade productFacade,
        BrandFacade brandFacade,
        LikeFacade likeFacade,
        ProductJpaRepository productJpaRepository,
        StockJpaRepository stockJpaRepository,
        BrandJpaRepository brandJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.productFacade = productFacade;
        this.brandFacade = brandFacade;
        this.likeFacade = likeFacade;
        this.productJpaRepository = productJpaRepository;
        this.stockJpaRepository = stockJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        brandId = brandFacade.create("나이키", "Just Do It").id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void softDeleteBrand(Long id) {
        BrandModel brand = brandJpaRepository.findById(id).orElseThrow();
        brand.delete();
        brandJpaRepository.save(brand);
    }

    @DisplayName("상품을 등록할 때, ")
    @Nested
    class CreateProduct {

        @DisplayName("Product 와 Stock 이 같은 트랜잭션에서 함께 저장되고, 응답에 purchasable 이 포함된다.")
        @Test
        void persistsProductAndStockTogether() {
            // given
            String name = "에어맥스 270";
            String description = "가벼운 쿠셔닝의 데일리 러닝화";
            Long price = 159_000L;
            Integer stock = 50;

            // when
            ProductInfo result = productFacade.createProduct(name, description, price, stock, brandId);

            // then
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo(name),
                () -> assertThat(result.description()).isEqualTo(description),
                () -> assertThat(result.price()).isEqualTo(price),
                () -> assertThat(result.purchasable()).isTrue(),
                () -> assertThat(productJpaRepository.findById(result.id()))
                    .hasValueSatisfying(p -> assertThat(p.getBrandId()).isEqualTo(brandId)),
                () -> assertThat(stockJpaRepository.findByProductId(result.id()))
                    .hasValueSatisfying(s -> assertThat(s.getQuantity()).isEqualTo(stock))
            );
        }

        @DisplayName("존재하지 않는 brandId 가 주어지면, BRAND_NOT_FOUND 예외가 발생하고 Product 와 Stock 은 저장되지 않는다.")
        @Test
        void throwsBrandNotFound_whenBrandIdDoesNotExist() {
            // given
            Long missingBrandId = brandId + 9999L;

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, missingBrandId));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND),
                () -> assertThat(productJpaRepository.count()).isZero(),
                () -> assertThat(stockJpaRepository.count()).isZero()
            );
        }

        @DisplayName("soft-deleted 된 brandId 가 주어지면, BRAND_NOT_FOUND 예외가 발생하고 Product 와 Stock 은 저장되지 않는다.")
        @Test
        void throwsBrandNotFound_whenBrandIsSoftDeleted() {
            // given
            softDeleteBrand(brandId);

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND),
                () -> assertThat(productJpaRepository.count()).isZero(),
                () -> assertThat(stockJpaRepository.count()).isZero()
            );
        }
    }

    @DisplayName("상품을 단건 조회할 때, ")
    @Nested
    class GetProduct {

        @DisplayName("좋아요가 없으면, 응답의 likeCount 는 0 이다.")
        @Test
        void returnsZeroLikeCount_whenProductHasNoLikes() {
            // given
            Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();

            // when
            ProductInfo result = productFacade.getProduct(productId);

            // then
            assertThat(result.likeCount()).isZero();
        }

        @DisplayName("좋아요가 누적되어 있으면, 누적된 likeCount 가 응답에 그대로 노출된다.")
        @Test
        void returnsAccumulatedLikeCount_whenLikesAreIncremented() {
            // given
            Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
            increaseLikes(productId, 3);

            // when
            ProductInfo result = productFacade.getProduct(productId);

            // then
            assertThat(result.likeCount()).isEqualTo(3L);
        }

        @DisplayName("재고가 0 인 상품을 조회하면, 응답의 purchasable 은 false 다.")
        @Test
        void returnsPurchasableFalse_whenStockIsZero() {
            // given
            Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 0, brandId).id();

            // when
            ProductInfo result = productFacade.getProduct(productId);

            // then
            assertThat(result.purchasable()).isFalse();
        }

        @DisplayName("재고가 1 이상이면, 응답의 purchasable 은 true 다.")
        @Test
        void returnsPurchasableTrue_whenStockIsPositive() {
            // given
            Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 1, brandId).id();

            // when
            ProductInfo result = productFacade.getProduct(productId);

            // then
            assertThat(result.purchasable()).isTrue();
        }

        @DisplayName("brand 가 soft-delete 되어도, 응답의 brand 정보는 그대로 노출된다.")
        @Test
        void exposesBrand_whenBrandIsSoftDeleted() {
            // given
            Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
            softDeleteBrand(brandId);

            // when
            ProductInfo result = productFacade.getProduct(productId);

            // then
            assertAll(
                () -> assertThat(result.brand().id()).isEqualTo(brandId),
                () -> assertThat(result.brand().name()).isEqualTo("나이키")
            );
        }

        private void increaseLikes(Long productId, int times) {
            for (int i = 0; i < times; i++) {
                likeFacade.like(likeUserIdSeq.getAndIncrement(), productId);
            }
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class UpdateProduct {

        @DisplayName("Product 정보와 Stock 의 quantity 가 새 값으로 갱신된다.")
        @Test
        void updatesProductFieldsAndStockQuantity() {
            // given
            Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();
            String newName = "에어맥스 270 SE";
            String newDescription = "스페셜 에디션 컬러웨이";
            Long newPrice = 179_000L;
            Integer newStock = 80;

            // when
            ProductInfo result = productFacade.updateProduct(productId, newName, newDescription, newPrice, newStock);

            // then
            assertAll(
                () -> assertThat(result.name()).isEqualTo(newName),
                () -> assertThat(result.description()).isEqualTo(newDescription),
                () -> assertThat(result.price()).isEqualTo(newPrice),
                () -> assertThat(result.purchasable()).isTrue(),
                () -> assertThat(stockJpaRepository.findByProductId(productId))
                    .hasValueSatisfying(s -> assertThat(s.getQuantity()).isEqualTo(newStock))
            );
        }

        @DisplayName("stock 을 더 작은 값으로 수정하면, Stock 의 quantity 가 감소한다.")
        @Test
        void decreasesStockQuantity_whenNewStockIsLess() {
            // given
            Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();

            // when
            productFacade.updateProduct(productId, "에어맥스 270", "데일리 러닝화", 159_000L, 12);

            // then
            assertThat(stockJpaRepository.findByProductId(productId))
                .hasValueSatisfying(s -> assertThat(s.getQuantity()).isEqualTo(12));
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    class GetAllProducts {

        @DisplayName("LATEST 정렬은, 최근에 등록한 상품부터 반환한다.")
        @Test
        void returnsLatestFirst_whenSortIsLatest() {
            // given
            ProductInfo chuck  = productFacade.createProduct("척테일러", "캔버스 클래식", 79_000L, 30, brandId);
            ProductInfo star   = productFacade.createProduct("슈퍼스타", "쉘토 스니커즈의 상징", 129_000L, 40, brandId);
            ProductInfo airmax = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);

            // when
            List<ProductInfo> result = productFacade.getAllProducts(ProductSortType.LATEST, 0, 10);

            // then
            assertThat(result).extracting(ProductInfo::id)
                .containsExactly(airmax.id(), star.id(), chuck.id());
        }

        @DisplayName("PRICE_ASC 정렬은, 가격이 낮은 상품부터 반환한다.")
        @Test
        void returnsLowestPriceFirst_whenSortIsPriceAsc() {
            // given
            ProductInfo airmax = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            ProductInfo chuck  = productFacade.createProduct("척테일러", "캔버스 클래식", 79_000L, 30, brandId);
            ProductInfo star   = productFacade.createProduct("슈퍼스타", "쉘토 스니커즈의 상징", 129_000L, 40, brandId);

            // when
            List<ProductInfo> result = productFacade.getAllProducts(ProductSortType.PRICE_ASC, 0, 10);

            // then
            assertThat(result).extracting(ProductInfo::id)
                .containsExactly(chuck.id(), star.id(), airmax.id());
        }

        @DisplayName("LIKES_DESC 정렬은, 좋아요 수가 많은 상품부터 반환한다.")
        @Test
        void returnsMostLikedFirst_whenSortIsLikesDesc() {
            // given
            ProductInfo airmax = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            ProductInfo chuck  = productFacade.createProduct("척테일러", "캔버스 클래식", 79_000L, 30, brandId);
            ProductInfo star   = productFacade.createProduct("슈퍼스타", "쉘토 스니커즈의 상징", 129_000L, 40, brandId);
            increaseLikes(airmax.id(), 5);
            increaseLikes(chuck.id(), 2);

            // when
            List<ProductInfo> result = productFacade.getAllProducts(ProductSortType.LIKES_DESC, 0, 10);

            // then
            assertThat(result).extracting(ProductInfo::id)
                .containsExactly(airmax.id(), chuck.id(), star.id());
        }

        @DisplayName("각 상품의 likeCount 가 응답에 포함된다.")
        @Test
        void includesLikeCountForEachProduct() {
            // given
            ProductInfo airmax = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            ProductInfo chuck  = productFacade.createProduct("척테일러", "캔버스 클래식", 79_000L, 30, brandId);
            ProductInfo star   = productFacade.createProduct("슈퍼스타", "쉘토 스니커즈의 상징", 129_000L, 40, brandId);
            increaseLikes(airmax.id(), 5);
            increaseLikes(chuck.id(), 2);

            // when
            List<ProductInfo> result = productFacade.getAllProducts(ProductSortType.LATEST, 0, 10);

            // then
            assertThat(result)
                .extracting(ProductInfo::id, ProductInfo::likeCount)
                .containsExactlyInAnyOrder(
                    tuple(airmax.id(), 5L),
                    tuple(chuck.id(), 2L),
                    tuple(star.id(), 0L)
                );
        }

        @DisplayName("page 와 size 로 슬라이싱하면, 해당 페이지의 상품만 반환한다.")
        @Test
        void returnsSlicedPage_whenPageAndSizeGiven() {
            // given
            ProductInfo first  = productFacade.createProduct("척테일러", "캔버스 클래식", 79_000L, 30, brandId);
            ProductInfo second = productFacade.createProduct("슈퍼스타", "쉘토 스니커즈의 상징", 129_000L, 40, brandId);
            ProductInfo third  = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            ProductInfo fourth = productFacade.createProduct("스탠스미스", "올타임 화이트 스니커즈", 119_000L, 25, brandId);

            // when
            List<ProductInfo> result = productFacade.getAllProducts(ProductSortType.LATEST, 1, 2);

            // then
            assertThat(result).extracting(ProductInfo::id)
                .containsExactly(second.id(), first.id());
        }

        @DisplayName("brand 가 soft-delete 되어도, 목록 응답의 각 상품 brand 정보는 그대로 노출된다.")
        @Test
        void exposesBrandForEachProduct_whenBrandIsSoftDeleted() {
            // given
            productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId);
            productFacade.createProduct("척테일러", "캔버스 클래식", 79_000L, 30, brandId);
            softDeleteBrand(brandId);

            // when
            List<ProductInfo> result = productFacade.getAllProducts(ProductSortType.LATEST, 0, 10);

            // then
            assertThat(result)
                .extracting(info -> info.brand().id(), info -> info.brand().name())
                .containsOnly(tuple(brandId, "나이키"));
        }

        private void increaseLikes(Long productId, int times) {
            for (int i = 0; i < times; i++) {
                likeFacade.like(likeUserIdSeq.getAndIncrement(), productId);
            }
        }
    }

    @DisplayName("상품을 삭제할 때, ")
    @Nested
    class DeleteProduct {

        @DisplayName("Product 와 Stock 이 함께 사라진다.")
        @Test
        void deletesProductAndStockTogether() {
            // given
            Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 159_000L, 50, brandId).id();

            // when
            productFacade.deleteProduct(productId);

            // then
            assertAll(
                () -> assertThat(productJpaRepository.findById(productId)).isEmpty(),
                () -> assertThat(stockJpaRepository.findByProductId(productId)).isEmpty()
            );
        }
    }
}
