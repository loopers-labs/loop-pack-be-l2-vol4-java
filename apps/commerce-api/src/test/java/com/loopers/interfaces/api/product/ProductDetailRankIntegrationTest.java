package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품 상세 응답의 실시간 rank 필드 검증 — 랭킹에 있을 때 1-indexed 순위, 없을 때 null.
 */
@SpringBootTest
class ProductDetailRankIntegrationTest {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired private ProductV1Controller productV1Controller;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private String allKey() {
        return "ranking:all:" + LocalDate.now().format(DAY_FMT);
    }

    private ProductModel givenProduct() {
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));
        stockRepository.save(StockModel.of(product.getId(), 10));
        return product;
    }

    @DisplayName("랭킹에 있는 상품의 상세 응답에는 1-indexed rank 가 실린다")
    @Test
    void detailIncludesLiveRank() {
        ProductModel top = givenProduct();
        ProductModel second = givenProduct();
        // 점수: second 가 더 높아 1위, top 이 2위
        redisTemplate.opsForZSet().add(allKey(), String.valueOf(second.getId()), 30.0);
        redisTemplate.opsForZSet().add(allKey(), String.valueOf(top.getId()), 10.0);

        ApiResponse<ProductV1Dto.ProductResponse> response = productV1Controller.getProductDetail(top.getId());

        assertThat(response.data().rank()).isEqualTo(2);
    }

    @DisplayName("랭킹에 없는 상품의 상세 응답 rank 는 null 이다")
    @Test
    void detailRankIsNullWhenNotRanked() {
        ProductModel product = givenProduct();

        ApiResponse<ProductV1Dto.ProductResponse> response = productV1Controller.getProductDetail(product.getId());

        assertThat(response.data().rank()).isNull();
    }
}
