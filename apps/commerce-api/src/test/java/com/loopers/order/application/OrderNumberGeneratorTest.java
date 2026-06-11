package com.loopers.order.application;

import com.loopers.order.domain.OrderNumberSequenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderNumberGeneratorTest {

    private final OrderNumberSequenceRepository sequenceRepository = mock(OrderNumberSequenceRepository.class);
    private final OrderNumberGenerator generator = new OrderNumberGenerator(sequenceRepository);

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Test
    @DisplayName("채번 값 1 이면 yyyyMMdd-000001 형식으로 발급한다")
    void givenSequenceOne_whenGenerate_thenReturnsZeroPaddedNumber() {
        when(sequenceRepository.nextValue(any())).thenReturn(1L);

        String orderNumber = generator.generate();

        assertThat(orderNumber).isEqualTo(today() + "-000001");
    }

    @Test
    @DisplayName("채번 값이 커도 6자리로 zero-padding 한다")
    void givenLargeSequence_whenGenerate_thenZeroPadsToSixDigits() {
        when(sequenceRepository.nextValue(any())).thenReturn(123L);

        String orderNumber = generator.generate();

        assertThat(orderNumber).isEqualTo(today() + "-000123");
    }

    @Test
    @DisplayName("오늘 날짜로 채번을 요청한다")
    void whenGenerate_thenRequestsSequenceForToday() {
        when(sequenceRepository.nextValue(LocalDate.now())).thenReturn(1L);

        String orderNumber = generator.generate();

        assertThat(orderNumber).startsWith(today() + "-");
    }
}
