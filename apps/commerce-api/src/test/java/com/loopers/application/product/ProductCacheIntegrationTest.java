package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSort;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductCacheIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisTemplate.delete(redisTemplate.keys("product:cache:*"));
    }

    @DisplayName("상품 상세 캐시")
    @Nested
    class ProductDetailCache {

        @DisplayName("캐시 미스 시 DB에서 조회하고 캐시에 저장한다.")
        @Test
        void savesToCache_onCacheMiss() {
            // Arrange
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity saved = productJpaRepository.save(new ProductEntity(brand.getId(), "티셔츠", BigDecimal.valueOf(15000)));

            // Act
            Product result = productService.getProduct(saved.getId());

            // Assert
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(redisTemplate.hasKey("product:cache:detail:" + saved.getId())).isTrue();
        }

        @DisplayName("캐시 히트 시 Redis에서 조회한다.")
        @Test
        void returnsFromCache_onCacheHit() {
            // Arrange
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            ProductEntity saved = productJpaRepository.save(new ProductEntity(brand.getId(), "티셔츠", BigDecimal.valueOf(15000)));
            productService.getProduct(saved.getId()); // 캐시 저장
            productJpaRepository.deleteById(saved.getId()); // DB에서 삭제해도

            // Act
            Product result = productService.getProduct(saved.getId()); // 캐시에서 반환

            // Assert
            assertThat(result.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("상품 수정 시 상세 캐시가 삭제된다.")
        @Test
        void evictsDetailCache_onUpdate() {
            // Arrange
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            Product product = productService.createProduct(brand.getId(), "티셔츠", BigDecimal.valueOf(15000), 10L);
            productService.getProduct(product.getId()); // 캐시 저장
            assertThat(redisTemplate.hasKey("product:cache:detail:" + product.getId())).isTrue();

            // Act
            productService.updateProduct(product.getId(), brand.getId(), "수정 티셔츠", BigDecimal.valueOf(20000), 5L);

            // Assert
            assertThat(redisTemplate.hasKey("product:cache:detail:" + product.getId())).isFalse();
        }

        @DisplayName("상품 삭제 시 상세 캐시가 삭제된다.")
        @Test
        void evictsDetailCache_onDelete() {
            // Arrange
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            Product product = productService.createProduct(brand.getId(), "티셔츠", BigDecimal.valueOf(15000), 10L);
            productService.getProduct(product.getId()); // 캐시 저장
            assertThat(redisTemplate.hasKey("product:cache:detail:" + product.getId())).isTrue();

            // Act
            productService.deleteProduct(product.getId());

            // Assert
            assertThat(redisTemplate.hasKey("product:cache:detail:" + product.getId())).isFalse();
        }
    }

    @DisplayName("상품 목록 캐시")
    @Nested
    class ProductListCache {

        @DisplayName("캐시 미스 시 DB에서 조회하고 캐시에 저장한다.")
        @Test
        void savesToCache_onCacheMiss() {
            // Arrange
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            productJpaRepository.save(new ProductEntity(brand.getId(), "티셔츠", BigDecimal.valueOf(15000)));

            // Act
            Page<Product> result = productService.getProducts(null, ProductSort.LATEST, PageRequest.of(0, 10));

            // Assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(redisTemplate.keys("product:cache:list:*")).isNotEmpty();
        }

        @DisplayName("상품 생성 시 목록 캐시가 전체 삭제된다.")
        @Test
        void evictsAllListCache_onCreate() {
            // Arrange
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            productService.getProducts(null, ProductSort.LATEST, PageRequest.of(0, 10)); // 캐시 저장
            assertThat(redisTemplate.keys("product:cache:list:*")).isNotEmpty();

            // Act
            productService.createProduct(brand.getId(), "신상품", BigDecimal.valueOf(30000), 5L);

            // Assert
            assertThat(redisTemplate.keys("product:cache:list:*")).isEmpty();
        }

        @DisplayName("상품 수정 시 목록 캐시가 전체 삭제된다.")
        @Test
        void evictsAllListCache_onUpdate() {
            // Arrange
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            Product product = productService.createProduct(brand.getId(), "티셔츠", BigDecimal.valueOf(15000), 10L);
            productService.getProducts(null, ProductSort.LATEST, PageRequest.of(0, 10)); // 캐시 저장
            assertThat(redisTemplate.keys("product:cache:list:*")).isNotEmpty();

            // Act
            productService.updateProduct(product.getId(), brand.getId(), "수정 티셔츠", BigDecimal.valueOf(20000), 5L);

            // Assert
            assertThat(redisTemplate.keys("product:cache:list:*")).isEmpty();
        }

        @DisplayName("sort 조건별로 별도 키에 캐시된다.")
        @Test
        void cachesSeparately_perSort() {
            // Arrange
            BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
            productJpaRepository.save(new ProductEntity(brand.getId(), "티셔츠", BigDecimal.valueOf(15000)));

            // Act
            productService.getProducts(null, ProductSort.LATEST, PageRequest.of(0, 10));
            productService.getProducts(null, ProductSort.PRICE_ASC, PageRequest.of(0, 10));
            productService.getProducts(null, ProductSort.LIKES_DESC, PageRequest.of(0, 10));

            // Assert
            assertThat(redisTemplate.keys("product:cache:list:*")).hasSize(3);
        }
    }
}