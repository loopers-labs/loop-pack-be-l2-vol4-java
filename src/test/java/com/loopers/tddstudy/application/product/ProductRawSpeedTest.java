package com.loopers.tddstudy.application.product;

import com.loopers.tddstudy.domain.brand.Brand;
import com.loopers.tddstudy.domain.brand.BrandRepository;
import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class ProductRawSpeedTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        Brand brand = new Brand("테스트브랜드", "설명", 1L);
        brand.publish();
        brandRepository.save(brand);

        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= 100_000; i++) {
            Product product = new Product("상품" + i, i * 100, 100, brand.getId());
            product.publish();
            products.add(product);
        }
        productRepository.saveAll(products);
    }

    @Test
    void DB_vs_Redis_원시속도_비교() {
        String cacheKey = "products:top10:raw";

        // Warmup
        List<Product> top10 = productRepository.findTop10ByOrderByLikeCountDesc();
        redisTemplate.opsForValue().set(cacheKey, top10);
        productRepository.findTop10ByOrderByLikeCountDesc();
        redisTemplate.opsForValue().get(cacheKey);

        // DB 100회
        long dbTotal = 0;
        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();
            productRepository.findTop10ByOrderByLikeCountDesc();
            dbTotal += System.nanoTime() - start;
        }
        System.out.printf("DB 조회 평균: %.3fms%n", dbTotal / 1_000_000.0 / 100);

        // Redis 100회
        long redisTotal = 0;
        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();
            redisTemplate.opsForValue().get(cacheKey);
            redisTotal += System.nanoTime() - start;
        }
        System.out.printf("Redis 조회 평균: %.3fms%n", redisTotal / 1_000_000.0 / 100);
    }
}
