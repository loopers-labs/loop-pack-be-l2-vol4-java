package com.loopers.infrastructure.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.application.brand.BrandRepository;
import com.loopers.application.like.LikeRepository;
import com.loopers.domain.like.ProductLikeModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("브랜드 필터 없이 페이지 및 정렬 조건에 맞는 상품 목록을 조회한다.")
    void findAll_WithoutBrandId_ShouldReturnAllProductsWithPagingAndSorting() throws InterruptedException {
        // given
        BrandModel brand1 = brandRepository.save(new BrandModel("Nike"));
        BrandModel brand2 = brandRepository.save(new BrandModel("Adidas"));

        ProductModel product1 = productRepository.save(new ProductModel(brand1.getId(), "Air Max", new BigDecimal("1000.0000")));
        Thread.sleep(10);
        ProductModel product2 = productRepository.save(new ProductModel(brand2.getId(), "Stan Smith", new BigDecimal("2000.0000")));
        Thread.sleep(10);
        ProductModel product3 = productRepository.save(new ProductModel(brand1.getId(), "Jordan", new BigDecimal("1500.0000")));

        // when (기본 정렬: latest)
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductModel> result = productRepository.findAll(null, "latest", pageable);

        // then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getId()).isEqualTo(product3.getId()); // 최신 상품이 첫 번째
        assertThat(result.getContent().get(1).getId()).isEqualTo(product2.getId());
        assertThat(result.getContent().get(2).getId()).isEqualTo(product1.getId());
    }

    @Test
    @DisplayName("브랜드 필터가 있는 경우 해당 브랜드의 상품만 조회된다.")
    void findAll_WithBrandId_ShouldReturnBrandProducts() {
        // given
        BrandModel brand1 = brandRepository.save(new BrandModel("Nike"));
        BrandModel brand2 = brandRepository.save(new BrandModel("Adidas"));

        ProductModel product1 = productRepository.save(new ProductModel(brand1.getId(), "Air Max", new BigDecimal("1000.0000")));
        ProductModel product2 = productRepository.save(new ProductModel(brand2.getId(), "Stan Smith", new BigDecimal("2000.0000")));

        // when
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductModel> result = productRepository.findAll(brand1.getId(), "latest", pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(product1.getId());
    }


    @Test
    @DisplayName("좋아요 많은 순 정렬 조건을 적용하여 상품 목록을 조회한다.")
    void findAll_WithSortLikesDesc_ShouldReturnSortedByLikesDesc() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));

        ProductModel product1 = productRepository.save(new ProductModel(brand.getId(), "Air Max 1", new BigDecimal("1000.0000")));
        Thread.sleep(10);
        ProductModel product2 = productRepository.save(new ProductModel(brand.getId(), "Air Max 2", new BigDecimal("2000.0000")));
        Thread.sleep(10);
        ProductModel product3 = productRepository.save(new ProductModel(brand.getId(), "Air Max 3", new BigDecimal("3000.0000")));

        // 좋아요 설정 (product2: 2개, product3: 1개, product1: 0개)
        product2.increaseLikeCount();
        product2.increaseLikeCount();
        productRepository.save(product2);

        product3.increaseLikeCount();
        productRepository.save(product3);

        likeRepository.save(new ProductLikeModel(1L, product2.getId()));
        likeRepository.save(new ProductLikeModel(2L, product2.getId()));
        likeRepository.save(new ProductLikeModel(1L, product3.getId()));

        // when
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductModel> result = productRepository.findAll(null, "likes_desc", pageable);

        // then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getId()).isEqualTo(product2.getId()); // 좋아요 2개
        assertThat(result.getContent().get(1).getId()).isEqualTo(product3.getId()); // 좋아요 1개
        assertThat(result.getContent().get(2).getId()).isEqualTo(product1.getId()); // 좋아요 0개
    }
}
