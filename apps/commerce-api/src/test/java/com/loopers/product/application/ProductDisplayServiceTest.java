package com.loopers.product.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.product.domain.ProductDetail;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductSortType;
import com.loopers.support.fake.IdFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDisplayServiceTest {

    private final ProductDisplayService service = new ProductDisplayService();

    private ProductModel product(long id, long brandId, long price) {
        return IdFixtures.assignId(new ProductModel(brandId, "상품" + id, "설명", price, 10), id);
    }

    private BrandModel brand(long id, String name) {
        return IdFixtures.assignId(new BrandModel(name, "브랜드 설명"), id);
    }

    @DisplayName("상세 조합 시 Product + Brand 이름 + 좋아요 수를 함께 담는다.")
    @Test
    void assembleDetail_combinesProductBrandAndLikeCount() {
        ProductModel product = product(1L, 1L, 1_000L);
        BrandModel brand = brand(1L, "나이키");

        ProductDetail detail = service.assembleDetail(product, brand, 42L);

        assertThat(detail.productId()).isEqualTo(1L);
        assertThat(detail.brandName()).isEqualTo("나이키");
        assertThat(detail.likeCount()).isEqualTo(42L);
    }

    @DisplayName("price_asc 정렬은 가격 오름차순으로 정렬한다.")
    @Test
    void assembleList_sortsByPriceAsc() {
        List<ProductModel> products =
            List.of(product(1L, 1L, 3_000L), product(2L, 1L, 1_000L), product(3L, 1L, 2_000L));
        Map<Long, BrandModel> brandMap = Map.of(1L, brand(1L, "브랜드"));
        Map<Long, Long> likeCounts = Map.of(1L, 0L, 2L, 0L, 3L, 0L);

        List<ProductDetail> result =
            service.assembleList(products, brandMap, likeCounts, ProductSortType.PRICE_ASC);

        assertThat(result).extracting(ProductDetail::price).containsExactly(1_000L, 2_000L, 3_000L);
    }

    @DisplayName("likes_desc 정렬은 좋아요 수 내림차순으로 정렬한다.")
    @Test
    void assembleList_sortsByLikesDesc() {
        List<ProductModel> products =
            List.of(product(1L, 1L, 1_000L), product(2L, 1L, 1_000L), product(3L, 1L, 1_000L));
        Map<Long, BrandModel> brandMap = Map.of(1L, brand(1L, "브랜드"));
        Map<Long, Long> likeCounts = Map.of(1L, 5L, 2L, 30L, 3L, 10L);

        List<ProductDetail> result =
            service.assembleList(products, brandMap, likeCounts, ProductSortType.LIKES_DESC);

        assertThat(result).extracting(ProductDetail::productId).containsExactly(2L, 3L, 1L);
    }

    @DisplayName("latest 정렬은 최신(식별자 내림차순) 순으로 정렬한다.")
    @Test
    void assembleList_sortsByLatest() {
        List<ProductModel> products =
            List.of(product(1L, 1L, 1_000L), product(3L, 1L, 1_000L), product(2L, 1L, 1_000L));
        Map<Long, BrandModel> brandMap = Map.of(1L, brand(1L, "브랜드"));
        Map<Long, Long> likeCounts = Map.of(1L, 0L, 2L, 0L, 3L, 0L);

        List<ProductDetail> result =
            service.assembleList(products, brandMap, likeCounts, ProductSortType.LATEST);

        assertThat(result).extracting(ProductDetail::productId).containsExactly(3L, 2L, 1L);
    }
}
