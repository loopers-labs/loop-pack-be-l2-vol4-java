package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductFacadeRankTest {

    @Autowired ProductFacade productFacade;
    @Autowired ProductRepository productRepository;
    @Autowired BrandRepository brandRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @Autowired RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Product seedProduct() {
        Brand brand = brandRepository.save(new Brand("나이키", "Just Do It"));
        return productRepository.save(new Product(brand.getId(), "상품", "d", 1000L, 10));
    }

    @DisplayName("오늘 랭킹에 있는 상품 상세는 1-based rank 를 담는다.")
    @Test
    void detailHasRankWhenRanked() {
        Product product = seedProduct();
        String todayKey = RankingKeys.daily(LocalDate.now(RankingKeys.ZONE));
        redisTemplate.opsForZSet().add(todayKey, "99999", 9.0);                       // 1위 (타상품)
        redisTemplate.opsForZSet().add(todayKey, String.valueOf(product.getId()), 5.0); // 2위

        ProductDetailInfo info = productFacade.getProductDetail(product.getId());

        assertThat(info.rank()).isEqualTo(2);
    }

    @DisplayName("랭킹에 없는 상품 상세의 rank 는 null 이다.")
    @Test
    void detailRankNullWhenNotRanked() {
        Product product = seedProduct();

        ProductDetailInfo info = productFacade.getProductDetail(product.getId());

        assertThat(info.rank()).isNull();
    }

    @DisplayName("상세가 캐시 히트여도 rank 는 실시간이다 — 캐시에 순위가 얼지 않는다.")
    @Test
    void rankIsLiveEvenOnCacheHit() {
        Product product = seedProduct();
        String todayKey = RankingKeys.daily(LocalDate.now(RankingKeys.ZONE));

        ProductDetailInfo first = productFacade.getProductDetail(product.getId()); // 캐시 적재 (rank null)
        redisTemplate.opsForZSet().add(todayKey, String.valueOf(product.getId()), 5.0); // 이후 랭킹 진입
        ProductDetailInfo second = productFacade.getProductDetail(product.getId()); // 캐시 히트 경로

        assertThat(first.rank()).isNull();
        assertThat(second.rank()).isEqualTo(1);
    }
}
