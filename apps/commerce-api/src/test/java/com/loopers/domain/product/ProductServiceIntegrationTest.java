package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
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

@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private LikeCountSeeder likeCountSeeder;

    private Long brandId;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        brandId = brand.getId();
        // 가격 5만, 3만, 7만
        productRepository.save(new ProductModel(brand.getId(),"맨투맨", "심플", 30_000L));
        ProductModel mid = productRepository.save(new ProductModel(brand.getId(),"후드", "포근함", 50_000L));
        ProductModel expensive = productRepository.save(new ProductModel(brand.getId(),"패딩", "겨울", 70_000L));
        // 좋아요 수: cheap=0, mid=5, expensive=2 — 컬럼 직접 시드(운영은 Redis 흡수→배치 반영)
        likeCountSeeder.seed(mid.getId(), 5L);
        likeCountSeeder.seed(expensive.getId(), 2L);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("정렬 조회 시")
    @Nested
    class Search {

        @DisplayName("price_asc 정렬은 가격 오름차순으로 반환한다")
        @Test
        void returnsByPriceAsc() {
            // when
            Page<ProductModel> page = productService.search(brandId, SortOption.PRICE_ASC, PageRequest.of(0, 20));

            // then
            List<Long> prices = page.getContent().stream().map(p -> p.getPrice().value()).toList();
            assertThat(prices).containsExactly(30_000L, 50_000L, 70_000L);
        }

        @DisplayName("likes_desc 정렬은 좋아요 내림차순으로 반환한다")
        @Test
        void returnsByLikesDesc() {
            // when
            Page<ProductModel> page = productService.search(brandId, SortOption.LIKES_DESC, PageRequest.of(0, 20));

            // then
            List<Long> likes = page.getContent().stream().map(ProductModel::getLikeCount).toList();
            assertThat(likes).containsExactly(5L, 2L, 0L);
        }

        @DisplayName("latest 정렬은 생성 시간 내림차순으로 반환한다 (가장 최근에 저장된 것이 먼저)")
        @Test
        void returnsByLatest() {
            // when
            Page<ProductModel> page = productService.search(brandId, SortOption.LATEST, PageRequest.of(0, 20));

            // then - 마지막에 저장된 것이 가장 먼저
            assertThat(page.getContent().get(0).getName()).isEqualTo("패딩");
        }

        @DisplayName("brandId 필터를 적용하면 해당 브랜드의 상품만 조회된다")
        @Test
        void filtersByBrandId() {
            // given - 새 브랜드와 상품 추가
            BrandModel other = brandRepository.save(new BrandModel("OtherBrand", "다른 브랜드"));
            productRepository.save(new ProductModel(other.getId(), "다른상품", "설명", 10_000L));

            // when
            Page<ProductModel> filtered = productService.search(brandId, SortOption.LATEST, PageRequest.of(0, 20));

            // then
            assertThat(filtered.getTotalElements()).isEqualTo(3);
            assertThat(filtered.getContent()).allMatch(p -> p.getBrandId().equals(brandId));
        }
    }
}
