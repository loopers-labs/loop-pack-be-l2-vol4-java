package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.SortOption;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductAdminFacadeIntegrationTest {

    @Autowired
    private ProductAdminFacade productAdminFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandId;
    private Long cheapId;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        brandId = brand.getId();

        // 가격: 맨투맨 3만, 후드 5만, 패딩 7만
        ProductModel cheap = productRepository.save(new ProductModel(brand.getId(),"맨투맨", "심플", 30_000L));
        ProductModel mid = productRepository.save(new ProductModel(brand.getId(),"후드", "포근함", 50_000L));
        ProductModel expensive = productRepository.save(new ProductModel(brand.getId(),"패딩", "겨울", 70_000L));
        cheapId = cheap.getId();

        // 좋아요 수: 맨투맨 0, 후드 5, 패딩 2 — 원자 UPDATE로 시드
        for (int i = 0; i < 5; i++) productRepository.incrementLikeCount(mid.getId());
        for (int i = 0; i < 2; i++) productRepository.incrementLikeCount(expensive.getId());

        // 재고: 맨투맨 10, 후드 0, 패딩 3
        stockRepository.save(new StockModel(cheap.getId(), 10));
        stockRepository.save(new StockModel(mid.getId(), 0));
        stockRepository.save(new StockModel(expensive.getId(), 3));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("어드민 상품 목록 조회 시")
    @Nested
    class Search {

        @DisplayName("price_asc 정렬을 전달하면 가격 오름차순으로 반환하며 재고 수량이 함께 매핑된다")
        @Test
        void returnsByPriceAsc_withStockQuantity() {
            // when
            Page<ProductAdminInfo> page =
                productAdminFacade.search(brandId, SortOption.PRICE_ASC, PageRequest.of(0, 20));

            // then
            List<Long> prices = page.getContent().stream().map(ProductAdminInfo::price).toList();
            assertThat(prices).containsExactly(30_000L, 50_000L, 70_000L);
            // 가장 저렴한 맨투맨의 재고(10)가 합성되어 노출된다
            assertThat(page.getContent().get(0).stockQuantity()).isEqualTo(10);
        }

        @DisplayName("likes_desc 정렬을 전달하면 좋아요 내림차순으로 반환한다")
        @Test
        void returnsByLikesDesc() {
            // when
            Page<ProductAdminInfo> page =
                productAdminFacade.search(brandId, SortOption.LIKES_DESC, PageRequest.of(0, 20));

            // then
            List<Long> likeCounts = page.getContent().stream().map(ProductAdminInfo::likeCount).toList();
            assertThat(likeCounts).containsExactly(5L, 2L, 0L);
        }
    }

    @DisplayName("어드민 상품 단건 조회 시")
    @Nested
    class GetProduct {

        @DisplayName("상품·브랜드·재고 수량이 합성되어 ProductAdminInfo로 반환된다")
        @Test
        void returnsProductWithBrandAndStock() {
            // when
            ProductAdminInfo info = productAdminFacade.getProduct(cheapId);

            // then
            assertAll(
                () -> assertThat(info.id()).isEqualTo(cheapId),
                () -> assertThat(info.name()).isEqualTo("맨투맨"),
                () -> assertThat(info.brandId()).isEqualTo(brandId),
                () -> assertThat(info.brandName()).isEqualTo("Loopers"),
                () -> assertThat(info.stockQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("상품이 존재하지 않으면 NOT_FOUND가 발생한다")
        @Test
        void throwsNotFound_whenProductMissing() {
            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> productAdminFacade.getProduct(99_999L));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
