package com.loopers.application.stock;

import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockServiceTest {

    private StockService stockService;
    private FakeStockRepository fakeStockRepository;

    @BeforeEach
    void setUp() {
        fakeStockRepository = new FakeStockRepository();
        stockService = new StockService(fakeStockRepository);
    }

    @DisplayName("재고를 등록할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 Stock을 저장하면, 저장되어 반환된다.")
        @Test
        void savesStock_whenStockIsValid() {
            // arrange
            StockModel stock = new StockModel(1L, 10);

            // act
            StockModel saved = stockService.create(stock);

            // assert
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getProductId()).isEqualTo(1L);
            assertThat(saved.getQuantity()).isEqualTo(10);
        }
    }

    @DisplayName("재고를 단건 조회할 때,")
    @Nested
    class GetByProductId {

        @DisplayName("존재하는 productId로 조회하면, 재고 정보를 반환한다.")
        @Test
        void returnsStock_whenProductExists() {
            // arrange
            StockModel saved = stockService.create(new StockModel(1L, 10));

            // act
            StockModel result = stockService.getByProductId(saved.getProductId());

            // assert
            assertThat(result.getProductId()).isEqualTo(1L);
            assertThat(result.getQuantity()).isEqualTo(10);
        }

        @DisplayName("존재하지 않는 productId로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act & assert
            assertThatThrownBy(() -> stockService.getByProductId(nonExistentId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Decrease {

        @DisplayName("재고가 충분할 때 decrease를 호출하면, 수량이 차감된다.")
        @Test
        void decreasesQuantity_whenStockIsSufficient() {
            // arrange
            stockService.create(new StockModel(1L, 10));

            // act
            stockService.decrease(1L, 3);

            // assert
            assertThat(stockService.getByProductId(1L).getQuantity()).isEqualTo(7);
        }

        @DisplayName("재고가 부족할 때 decrease를 호출하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            stockService.create(new StockModel(1L, 5));

            // act & assert
            assertThatThrownBy(() -> stockService.decrease(1L, 10))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 productId로 decrease를 호출하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // act & assert
            assertThatThrownBy(() -> stockService.decrease(999L, 1))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고를 증가할 때,")
    @Nested
    class Increase {

        @DisplayName("increase를 호출하면, 수량이 증가한다.")
        @Test
        void increasesQuantity_whenCalled() {
            // arrange
            stockService.create(new StockModel(1L, 10));

            // act
            stockService.increase(1L, 5);

            // assert
            assertThat(stockService.getByProductId(1L).getQuantity()).isEqualTo(15);
        }
    }

    // ───────────────────────────────────────────────
    // Fake: DB 없이 비즈니스 로직만 격리 검증
    // @SQLRestriction 동작(soft delete 필터)은 재현하지 않음
    //   → 해당 동작은 StockServiceIntegrationTest에서 실제 DB로만 검증
    // ───────────────────────────────────────────────
    private static class FakeStockRepository implements StockRepository {

        private final Map<Long, StockModel> store = new HashMap<>();
        private long sequence = 1L;

        @Override
        public StockModel save(StockModel stock) {
            setId(stock, sequence++);
            store.put(stock.getProductId(), stock);
            return stock;
        }

        @Override
        public Optional<StockModel> findByProductId(Long productId) {
            return store.values().stream()
                .filter(s -> s.getProductId().equals(productId))
                .findFirst();
        }

        private void setId(StockModel stock, long id) {
            try {
                var field = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(stock, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
