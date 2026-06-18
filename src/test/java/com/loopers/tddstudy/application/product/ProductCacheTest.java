package com.loopers.tddstudy.application.product;

import com.loopers.tddstudy.domain.brand.Brand;
import com.loopers.tddstudy.domain.brand.BrandRepository;
import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StopWatch;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class ProductCacheTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @BeforeEach
    void setUp() {
        Brand brand = new Brand("테스트브랜드", "설명", 1L);
        brand.publish();
        brandRepository.save(brand);

        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= 100000; i++) {
            Product product = new Product("상품" + i, i * 100, 100, brand.getId());
            product.publish();
            products.add(product);
        }
        productRepository.saveAll(products);
    }

    @Test
    @DisplayName("캐시 미스 - DB 직접 조회 시간 측정")
    void db_조회_시간_측정() {
        StopWatch sw = new StopWatch();
        sw.start();
        productService.getAll(1L, "USER", null, "likes_desc");
        sw.stop();
        System.out.println("DB 조회: " + sw.getTotalTimeMillis() + "ms");
    }

    @Test
    @DisplayName("캐시 히트 - Redis 조회 시간 측정")
    void redis_조회_시간_측정() {
        productService.getAll(1L, "USER", null, "likes_desc");

        StopWatch sw = new StopWatch();
        sw.start();
        productService.getAll(1L, "USER", null, "likes_desc");
        sw.stop();
        System.out.println("Redis 조회: " + sw.getTotalTimeMillis() + "ms");
    }
}
