package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductLikeSortIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createProduct(Long brandId, String name, long likeCount) {
        ProductModel product = productJpaRepository.save(new ProductModel(brandId, name, "설명", 1000L, 10));
        jdbcTemplate.update("UPDATE product SET like_count = ? WHERE id = ?", likeCount, product.getId());
        return product.getId();
    }

    @DisplayName("좋아요순(DESC) 정렬 시, like_count 내림차순으로 정렬된다.")
    @Test
    void sortsByLikeCountDesc() {
        // arrange
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", null, null));
        createProduct(brand.getId(), "적음", 5L);
        createProduct(brand.getId(), "많음", 100L);
        createProduct(brand.getId(), "보통", 50L);

        // act
        ProductSearchCondition condition =
            ProductSearchCondition.of(null, "likeCount", "desc", 0, 20);
        List<ProductModel> products = productRepository.search(condition).products();

        // assert
        assertThat(products).extracting(ProductModel::getName)
            .containsExactly("많음", "보통", "적음");
    }

    @DisplayName("브랜드 필터와 좋아요순 정렬을 함께 적용하면, 해당 브랜드 상품만 like_count 내림차순으로 반환한다.")
    @Test
    void filtersByBrandAndSortsByLikeCount() {
        // arrange
        BrandModel nike = brandJpaRepository.save(new BrandModel("나이키", null, null));
        BrandModel adidas = brandJpaRepository.save(new BrandModel("아디다스", null, null));
        createProduct(nike.getId(), "나이키-인기", 100L);
        createProduct(nike.getId(), "나이키-비인기", 10L);
        createProduct(adidas.getId(), "아디다스-최고인기", 999L);

        // act
        ProductSearchCondition condition =
            ProductSearchCondition.of(nike.getId(), "likeCount", "desc", 0, 20);
        List<ProductModel> products = productRepository.search(condition).products();

        // assert
        assertThat(products).extracting(ProductModel::getName)
            .containsExactly("나이키-인기", "나이키-비인기");
    }

    @DisplayName("like_count 동점이면, id 내림차순으로 안정적으로 정렬된다.")
    @Test
    void breaksTieById() {
        // arrange
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", null, null));
        Long first = createProduct(brand.getId(), "먼저", 50L);
        Long second = createProduct(brand.getId(), "나중", 50L);

        // act
        ProductSearchCondition condition =
            ProductSearchCondition.of(null, "likeCount", "desc", 0, 20);
        List<ProductModel> products = productRepository.search(condition).products();

        // assert : 동점이면 id DESC → 나중(더 큰 id)이 먼저
        assertThat(products).extracting(ProductModel::getId)
            .containsExactly(second, first);
    }
}
