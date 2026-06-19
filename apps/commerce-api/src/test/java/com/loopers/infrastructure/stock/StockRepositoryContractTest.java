package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Transactional
abstract class StockRepositoryContractTest {

    abstract StockRepository repository();

    @DisplayName("재고를 원자적으로 차감할 때, 재고가 요청 수량보다 적으면 0을 반환하고 수량이 변하지 않는다.")
    @Test
    void decreaseQuantity_returns0AndKeepsQuantityUnchanged_whenStockIsInsufficient() {
        // given
        Long productId = 1L;
        repository().save(new StockModel(productId, 3L));

        // when
        int result = repository().decreaseQuantity(productId, 5L);

        // then
        StockModel stock = repository().findByProductId(productId).orElseThrow();
        assertAll(
                () -> assertThat(result).isEqualTo(0),
                () -> assertThat(stock.getQuantity()).isEqualTo(3L)
        );
    }
}
