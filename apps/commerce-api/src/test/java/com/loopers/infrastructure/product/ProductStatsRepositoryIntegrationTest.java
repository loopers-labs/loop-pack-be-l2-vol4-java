package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@SpringBootTest
class ProductStatsRepositoryIntegrationTest extends ProductStatsRepositoryContractTest {

    @Autowired
    private ProductStatsRepository productStatsRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager em;

    @Override
    ProductStatsRepository repository() {
        return productStatsRepository;
    }

    @Override
    ProductRepository productRepository() {
        return productRepository;
    }

    @DisplayName("좋아요 수를 원자적으로 증가시킬 때, likeCount 가 1 증가한다.")
    @Test
    void increaseLikeCount_increasesLikeCountByOne() {
        // given
        ProductModel product = productRepository.save(new ProductModel(1L, "상품B", BigDecimal.valueOf(10000)));
        productStatsRepository.save(new ProductStatsModel(product));

        // when
        productStatsRepository.increaseLikeCount(product.getId());
        em.clear();

        // then
        ProductStatsModel found = productStatsRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(found.getLikeCount()).isEqualTo(1L);
    }

    @DisplayName("좋아요 수를 원자적으로 감소시킬 때, likeCount 가 1 감소한다.")
    @Test
    void decreaseLikeCount_decreasesLikeCountByOne() {
        // given
        ProductModel product = productRepository.save(new ProductModel(1L, "상품C", BigDecimal.valueOf(10000)));
        productStatsRepository.save(new ProductStatsModel(product));
        productStatsRepository.increaseLikeCount(product.getId());

        // when
        productStatsRepository.decreaseLikeCount(product.getId());
        em.clear();

        // then
        ProductStatsModel found = productStatsRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(found.getLikeCount()).isEqualTo(0L);
    }
}
