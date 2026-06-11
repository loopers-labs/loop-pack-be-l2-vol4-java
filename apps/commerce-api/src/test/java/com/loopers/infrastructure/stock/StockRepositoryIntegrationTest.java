package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Transactional
@SpringBootTest
class StockRepositoryIntegrationTest extends StockRepositoryContractTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private EntityManager em;

    @Override
    StockRepository repository() {
        return stockRepository;
    }

    @DisplayName("재고를 원자적으로 차감할 때, 재고가 요청 수량보다 많으면 1을 반환하고 수량이 차감된다.")
    @Test
    void decreaseQuantity_returns1_whenStockIsSufficient() {
        // given
        Long productId = 2L;
        stockRepository.save(new StockModel(productId, 10L));

        // when
        int result = stockRepository.decreaseQuantity(productId, 3L);
        em.clear();

        // then
        StockModel stock = stockRepository.findByProductId(productId).orElseThrow();
        assertAll(
                () -> assertThat(result).isEqualTo(1),
                () -> assertThat(stock.getQuantity()).isEqualTo(7L)
        );
    }

    @DisplayName("재고를 원자적으로 차감할 때, 재고가 요청 수량과 정확히 같으면 1을 반환하고 수량이 0이 된다.")
    @Test
    void decreaseQuantity_returns1_whenStockEqualsRequestedQuantity() {
        // given
        Long productId = 3L;
        stockRepository.save(new StockModel(productId, 5L));

        // when
        int result = stockRepository.decreaseQuantity(productId, 5L);
        em.clear();

        // then
        StockModel stock = stockRepository.findByProductId(productId).orElseThrow();
        assertAll(
                () -> assertThat(result).isEqualTo(1),
                () -> assertThat(stock.getQuantity()).isEqualTo(0L)
        );
    }
}
