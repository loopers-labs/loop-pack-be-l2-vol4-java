package com.loopers.application.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.infrastructure.product.ProductCacheStore;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

// 캐시(Redis) 연결이 끊긴 상황을 시뮬레이션 — 공유 Redis 컨테이너는 그대로 두고, 잘못된 포트로만 별도 ProductCacheStore를 구성한다.
@SpringBootTest
class ProductFacadeFailOpenIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ProductStatsRepository productStatsRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @TestConfiguration
    static class BrokenCacheConfig {

        @Primary
        @Bean
        public ProductCacheStore brokenProductCacheStore(ObjectMapper objectMapper) {
            LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory("localhost", 19999);
            connectionFactory.afterPropertiesSet();

            StringRedisSerializer serializer = new StringRedisSerializer();
            RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
            redisTemplate.setKeySerializer(serializer);
            redisTemplate.setValueSerializer(serializer);
            redisTemplate.setConnectionFactory(connectionFactory);
            redisTemplate.afterPropertiesSet();

            return new ProductCacheStore(redisTemplate, objectMapper);
        }
    }

    @DisplayName("Redis 캐시에 연결할 수 없는 상황에서도,")
    @Test
    void fallsBackToDatabase_whenRedisIsUnavailable() {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "에어맥스", BigDecimal.valueOf(150000)));
        productStatsRepository.save(new ProductStatsModel(product));
        stockRepository.save(new StockModel(product.getId(), 10L));

        // when & then
        ProductInfo productInfo = assertDoesNotThrow(() -> productFacade.getProduct(product.getId()));
        assertThat(productInfo.name()).isEqualTo("에어맥스");

        Page<ProductInfo> page = assertDoesNotThrow(
                () -> productFacade.getProducts(brand.getId(), PageRequest.of(0, 20)));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }
}
