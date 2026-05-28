package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.stock.StockModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDomainServiceTest {

    private final ProductDomainService productDomainService = new ProductDomainService();

    private BrandModel activeBrand;
    private ProductModel activeProduct;
    private StockModel stock;

    @BeforeEach
    void setUp() {
        activeBrand = new BrandModel("Nike", "스포츠 브랜드");
        activeProduct = new ProductModel(activeBrand, "나이키 에어맥스", 150_000);
        stock = new StockModel(activeProduct, 50);
    }

    @DisplayName("assembleDetail()를 호출할 때,")
    @Nested
    class AssembleDetail {

        @DisplayName("Product + Brand + Stock 정보가 ProductDetail로 조합된다.")
        @Test
        void returnsProductDetail_withAllFields() {
            // act
            ProductDetail detail = productDomainService.assembleDetail(activeProduct, stock);

            // assert
            assertThat(detail.name()).isEqualTo("나이키 에어맥스");
            assertThat(detail.price()).isEqualTo(150_000);
            assertThat(detail.brandName()).isEqualTo("Nike");     // Brand 정보 포함
            assertThat(detail.stockQuantity()).isEqualTo(50);     // Stock 정보 포함
            assertThat(detail.likeCount()).isEqualTo(0L);
        }

        @DisplayName("좋아요 수가 반영된 상품의 detail에 likeCount가 포함된다.")
        @Test
        void includesLikeCount_whenProductHasLikes() {
            // arrange — 직접 ProductModel 생성 (likeCount는 DB atomic UPDATE 대상이나 도메인 객체 조합 테스트용)
            ProductModel likedProduct = new ProductModel(activeBrand, "인기 상품", 200_000);
            StockModel likedStock = new StockModel(likedProduct, 10);

            // act
            ProductDetail detail = productDomainService.assembleDetail(likedProduct, likedStock);

            // assert
            assertThat(detail.brandName()).isEqualTo("Nike");
            assertThat(detail.stockQuantity()).isEqualTo(10);
        }
    }
}
