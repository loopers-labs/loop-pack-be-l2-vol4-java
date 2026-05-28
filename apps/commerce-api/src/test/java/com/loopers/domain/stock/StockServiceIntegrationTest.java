package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * StockService 통합 — H2에서 실제 IN 쿼리·unique 제약·영속 상태를 검증한다.
 * 단위 테스트(StockServiceTest, StockModelTest)는 분기/불변식, 통합은 DB 동작을 본다.
 */
@SpringBootTest
class StockServiceIntegrationTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long productId1;
    private Long productId2;

    @BeforeEach
    void setUp() {
        productId1 = 1001L;
        productId2 = 1002L;
        stockRepository.save(new StockModel(productId1, 10));
        stockRepository.save(new StockModel(productId2, 5));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 차감 시")
    @Nested
    class Decrease {

        @DisplayName("정상 차감되면 DB에도 quantity 변경이 반영된다 (dirty checking)")
        @Test
        void persistsQuantityChange() {
            // when
            stockService.decrease(productId1, 3);

            // then
            assertThat(stockRepository.findByProductId(productId1).orElseThrow().getQuantity()).isEqualTo(7);
        }
    }

    @DisplayName("productId로 다건 조회 시")
    @Nested
    class GetQuantities {

        @DisplayName("IN 쿼리로 일괄 조회되며 존재하지 않는 productId는 결과에서 빠진다")
        @Test
        void returnsMap_filteringMissingProductIds() {
            // when
            Map<Long, Integer> quantities = stockService.getQuantities(List.of(productId1, productId2, 9_999L));

            // then
            assertAll(
                () -> assertThat(quantities).hasSize(2),
                () -> assertThat(quantities).containsEntry(productId1, 10).containsEntry(productId2, 5)
            );
        }

        @DisplayName("빈 컬렉션을 전달하면 빈 Map을 반환한다")
        @Test
        void returnsEmptyMap_whenInputEmpty() {
            // when
            Map<Long, Integer> quantities = stockService.getQuantities(List.of());

            // then
            assertThat(quantities).isEmpty();
        }
    }

    @DisplayName("재고 생성 시")
    @Nested
    class Create {

        @DisplayName("같은 productId로 재고를 두 번 저장하면 unique 제약(product_id UNIQUE) 위반으로 실패한다")
        @Test
        void violatesUniqueConstraint_whenDuplicateProductId() {
            // given - setUp에서 productId1 재고가 이미 존재

            // when / then
            assertThatThrownBy(() -> stockRepository.save(new StockModel(productId1, 7)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @DisplayName("decreaseAll 일괄 차감 시")
    @Nested
    class DecreaseAll {

        @DisplayName("여러 상품의 재고를 한 번에 차감하면 모두 DB에 반영된다")
        @Test
        void decreasesAllInOnePass() {
            // when
            stockService.decreaseAll(Map.of(productId1, 2, productId2, 1));

            // then
            assertAll(
                () -> assertThat(stockRepository.findByProductId(productId1).orElseThrow().getQuantity()).isEqualTo(8),
                () -> assertThat(stockRepository.findByProductId(productId2).orElseThrow().getQuantity()).isEqualTo(4)
            );
        }

        @DisplayName("일괄 차감 도중 한 상품이라도 재고 부족이면 CONFLICT가 발생하고 트랜잭션은 전체 롤백된다")
        @Test
        void rollsBackAll_whenAnyStockInsufficient() {
            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> stockService.decreaseAll(Map.of(productId1, 2, productId2, 999)));

            // then
            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(stockRepository.findByProductId(productId1).orElseThrow().getQuantity()).isEqualTo(10),
                () -> assertThat(stockRepository.findByProductId(productId2).orElseThrow().getQuantity()).isEqualTo(5)
            );
        }
    }
}
