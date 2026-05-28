package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProductStockServiceIntegrationTest {

    @Autowired
    private ProductStockService productStockService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 생성·조회")
    @Nested
    class CreateAndGet {

        @DisplayName("createStock 후 getStock으로 동일한 재고를 조회할 수 있다.")
        @Test
        void roundTrip() {
            // given
            ProductStockModel created = productStockService.createStock(1L, 10);

            // when
            ProductStockModel found = productStockService.getStock(1L);

            // then
            assertThat(found.getId()).isEqualTo(created.getId());
            assertThat(found.getStock().value()).isEqualTo(10);
        }

        @DisplayName("존재하지 않는 productId면, 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNoStock() {
            CoreException result = assertThrows(CoreException.class,
                    () -> productStockService.getStock(99L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고 변경할 때")
    @Nested
    class ChangeStock {

        @DisplayName("새 수량으로 갱신된다.")
        @Test
        void updatesStock() {
            // given
            productStockService.createStock(1L, 10);

            // when
            productStockService.changeStock(1L, 5);

            // then
            ProductStockModel found = productStockService.getStock(1L);
            assertThat(found.getStock().value()).isEqualTo(5);
        }

        @DisplayName("존재하지 않는 productId면, 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNoStock() {
            CoreException result = assertThrows(CoreException.class,
                    () -> productStockService.changeStock(99L, 5));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고 삭제할 때")
    @Nested
    class DeleteStock {

        @DisplayName("soft delete되어 getStock에서 조회되지 않는다.")
        @Test
        void softDeletes() {
            // given
            productStockService.createStock(1L, 10);

            // when
            productStockService.deleteStock(1L);

            // then
            CoreException result = assertThrows(CoreException.class,
                    () -> productStockService.getStock(1L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고 차감할 때")
    @Nested
    class DecreaseStock {

        @DisplayName("재고가 충분하면, 차감되어 남은 재고가 반영된다.")
        @Test
        void decreases_whenSufficient() {
            // given
            productStockService.createStock(1L, 10);

            // when
            productStockService.decreaseStock(1L, 3);

            // then
            ProductStockModel found = productStockService.getStock(1L);
            assertThat(found.getStock().value()).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenInsufficient() {
            // given
            productStockService.createStock(1L, 2);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productStockService.decreaseStock(1L, 5));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("차감 수량이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNonPositive() {
            // given
            productStockService.createStock(1L, 10);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productStockService.decreaseStock(1L, 0));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 productId면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNoStock() {
            CoreException result = assertThrows(CoreException.class,
                    () -> productStockService.decreaseStock(99L, 1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고 복구할 때")
    @Nested
    class IncreaseStock {

        @DisplayName("복구 수량만큼 재고가 증가한다.")
        @Test
        void increases() {
            // given
            productStockService.createStock(1L, 5);

            // when
            productStockService.increaseStock(1L, 3);

            // then
            ProductStockModel found = productStockService.getStock(1L);
            assertThat(found.getStock().value()).isEqualTo(8);
        }

        @DisplayName("복구 수량이 0 이하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNonPositive() {
            // given
            productStockService.createStock(1L, 5);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productStockService.increaseStock(1L, 0));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}