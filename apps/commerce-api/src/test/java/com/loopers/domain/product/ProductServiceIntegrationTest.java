package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.enums.ProductSortType;
import com.loopers.domain.product.enums.ProductStatus;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.wishlist.WishlistModel;
import com.loopers.domain.wishlist.WishlistRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductStockRepository productStockRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private WishlistRepository wishlistRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private BrandModel brand;

    @BeforeEach
    void setUp() {
        brand = brandRepository.save(new BrandModel("테스트브랜드"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel saveProduct(String name) {
        return productRepository.save(new ProductModel(brand.getId(), new ProductName(name)));
    }

    private void saveStock(ProductModel product, long price, int quantity) {
        productStockRepository.save(new ProductStockModel(product, new Price(price), quantity));
    }

    private void saveWishlist(long userId, long productId) {
        wishlistRepository.save(new WishlistModel(userId, productId));
    }

    @DisplayName("상품 생성 시,")
    @Nested
    class Create {

        @DisplayName("유효한 입력이면, 상품이 저장된다.")
        @Test
        void createsProduct_whenInputsAreValid() {
            ProductModel result = productService.create(brand.getId(), new ProductName("신규상품"));

            assertThat(result.getName()).isEqualTo("신규상품");
            assertThat(result.getBrandId()).isEqualTo(brand.getId());
        }

        @DisplayName("같은 브랜드 내 동일한 이름의 상품이 존재하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenProductNameAlreadyExistsInBrand() {
            saveProduct("테스트상품");

            CoreException exception = assertThrows(CoreException.class,
                    () -> productService.create(brand.getId(), new ProductName("테스트상품")));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("상품 삭제 시,")
    @Nested
    class Delete {

        @DisplayName("상품이 존재하면, 비활성화 처리된다.")
        @Test
        void deactivatesProduct_whenProductExists() {
            ProductModel product = saveProduct("삭제상품");

            productService.delete(product.getId());

            ProductModel deleted = productRepository.find(product.getId()).get();
            assertThat(deleted.getStatus()).isEqualTo(ProductStatus.INACTIVE);
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("삭제된 상품은 목록 조회에서 제외된다.")
        @Test
        void excludesDeletedProduct_fromGetList() {
            ProductModel active = saveProduct("활성상품");
            ProductModel deleted = saveProduct("삭제상품");
            productService.delete(deleted.getId());

            Page<ProductModel> result = productService.getList(null, ProductSortType.LATEST, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("활성상품");
        }

    }

    @DisplayName("상품 목록 조회 시,")
    @Nested
    class GetList {

        @DisplayName("LATEST 정렬: 최근 등록된 상품이 먼저 반환된다.")
        @Test
        void returnsLatestFirst_whenSortIsLatest() {
            ProductModel older = saveProduct("구형상품");
            ProductModel newer = saveProduct("신형상품");

            Page<ProductModel> result = productService.getList(null, ProductSortType.LATEST, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getId()).isEqualTo(newer.getId());
            assertThat(result.getContent().get(1).getId()).isEqualTo(older.getId());
        }

        @DisplayName("PRICE_ASC 정렬: 재고 최저가 기준 낮은 순으로 반환된다.")
        @Test
        void returnsByMinPriceAscending_whenSortIsPriceAsc() {
            ProductModel cheap = saveProduct("저가상품");
            ProductModel mid = saveProduct("중가상품");
            ProductModel expensive = saveProduct("고가상품");
            saveStock(cheap, 10000L, 5);
            saveStock(mid, 20000L, 5);
            saveStock(expensive, 30000L, 5);

            Page<ProductModel> result = productService.getList(null, ProductSortType.PRICE_ASC, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getContent().get(0).getId()).isEqualTo(cheap.getId());
            assertThat(result.getContent().get(1).getId()).isEqualTo(mid.getId());
            assertThat(result.getContent().get(2).getId()).isEqualTo(expensive.getId());
        }

        @DisplayName("PRICE_ASC 정렬: 재고가 없는 상품은 맨 뒤에 반환된다.")
        @Test
        void returnsProductWithNoStockLast_whenSortIsPriceAsc() {
            ProductModel withStock = saveProduct("재고있는상품");
            ProductModel noStock = saveProduct("재고없는상품");
            saveStock(withStock, 20000L, 5);

            Page<ProductModel> result = productService.getList(null, ProductSortType.PRICE_ASC, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getId()).isEqualTo(withStock.getId());
            assertThat(result.getContent().get(1).getId()).isEqualTo(noStock.getId());
        }

        @DisplayName("LIKES_DESC 정렬: 찜 수 내림차순으로 반환된다.")
        @Test
        void returnsByLikesDescending_whenSortIsLikesDesc() {
            ProductModel mostLiked = saveProduct("인기상품");
            ProductModel lessLiked = saveProduct("보통상품");
            ProductModel noLike = saveProduct("비인기상품");
            saveWishlist(1L, mostLiked.getId());
            saveWishlist(2L, mostLiked.getId());
            saveWishlist(1L, lessLiked.getId());

            Page<ProductModel> result = productService.getList(null, ProductSortType.LIKES_DESC, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getContent().get(0).getId()).isEqualTo(mostLiked.getId());
            assertThat(result.getContent().get(1).getId()).isEqualTo(lessLiked.getId());
            assertThat(result.getContent().get(2).getId()).isEqualTo(noLike.getId());
        }

        @DisplayName("브랜드 ID로 필터링하면, 해당 브랜드의 상품만 반환된다.")
        @Test
        void filtersProductsByBrandId() {
            BrandModel otherBrand = brandRepository.save(new BrandModel("다른브랜드"));
            saveProduct("내브랜드상품");
            productRepository.save(new ProductModel(otherBrand.getId(), new ProductName("다른브랜드상품")));

            Page<ProductModel> result = productService.getList(brand.getId(), ProductSortType.LATEST, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("내브랜드상품");
        }
    }
}