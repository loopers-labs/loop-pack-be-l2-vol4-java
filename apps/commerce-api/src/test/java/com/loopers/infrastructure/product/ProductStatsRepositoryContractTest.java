package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Transactional
abstract class ProductStatsRepositoryContractTest {

    abstract ProductStatsRepository repository();

    abstract ProductRepository productRepository();

    @DisplayName("좋아요 수를 원자적으로 감소시킬 때, likeCount 가 0 이면 감소하지 않는다.")
    @Test
    void decreaseLikeCount_doesNotDecreaseBelowZero_whenLikeCountIsZero() {
        // given
        ProductModel product = productRepository().save(new ProductModel(1L, "상품A", BigDecimal.valueOf(10000)));
        repository().save(new ProductStatsModel(product));

        // when
        repository().decreaseLikeCount(product.getId());

        // then
        ProductStatsModel found = repository().findByProductId(product.getId()).orElseThrow();
        assertAll(
                () -> assertThat(found.getLikeCount()).isEqualTo(0L),
                () -> assertThat(found.getLikeCount()).isGreaterThanOrEqualTo(0L)
        );
    }
}
