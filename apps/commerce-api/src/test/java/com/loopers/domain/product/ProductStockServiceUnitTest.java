package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductStockServiceUnitTest {

    @Mock
    private ProductStockRepository productStockRepository;

    @InjectMocks
    private ProductStockService productStockService;

    @DisplayName("재고 단건 조회할 때")
    @Nested
    class GetStock {

        @DisplayName("존재한다면, 재고 모델을 반환한다.")
        @Test
        void returnsStock_whenExists() {
            // given
            ProductStockModel model = ProductStockModel.of(1L, Stock.of(10));
            given(productStockRepository.findByProductId(1L)).willReturn(Optional.of(model));

            // when
            ProductStockModel result = productStockService.getStock(1L);

            // then
            assertThat(result.getStock().value()).isEqualTo(10);
        }

        @DisplayName("존재하지 않으면, 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotExists() {
            // given
            given(productStockRepository.findByProductId(1L)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> productStockService.getStock(1L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고 생성할 때")
    @Nested
    class CreateStock {

        @DisplayName("주어진 수량으로 모델을 만들어 저장한다.")
        @Test
        void savesNewModel() {
            // given
            ArgumentCaptor<ProductStockModel> captor = ArgumentCaptor.forClass(ProductStockModel.class);
            given(productStockRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            // when
            productStockService.createStock(1L, 5);

            // then
            verify(productStockRepository).save(captor.capture());
            ProductStockModel saved = captor.getValue();
            assertThat(saved.getProductId()).isEqualTo(1L);
            assertThat(saved.getStock().value()).isEqualTo(5);
        }

        @DisplayName("음수 수량이면, 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNegative() {
            CoreException result = assertThrows(CoreException.class,
                    () -> productStockService.createStock(1L, -1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}