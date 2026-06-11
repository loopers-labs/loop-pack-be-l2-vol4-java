package com.loopers.order.application;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderNumberGeneratorIntegrationTest {

    private final OrderNumberGenerator orderNumberGenerator;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderNumberGeneratorIntegrationTest(
            OrderNumberGenerator orderNumberGenerator,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.orderNumberGenerator = orderNumberGenerator;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Test
    @DisplayName("연속 채번하면 같은 날짜 안에서 순번이 1, 2, 3 으로 증가한다")
    void whenGenerateRepeatedly_thenSequenceIncrementsWithinSameDate() {
        String first = orderNumberGenerator.generate();
        String second = orderNumberGenerator.generate();
        String third = orderNumberGenerator.generate();

        assertThat(first).isEqualTo(today() + "-000001");
        assertThat(second).isEqualTo(today() + "-000002");
        assertThat(third).isEqualTo(today() + "-000003");
    }

    @Test
    @DisplayName("채번은 중복 없이 고유한 주문번호를 발급한다")
    void whenGenerateManyTimes_thenAllNumbersAreUnique() {
        var numbers = new java.util.HashSet<String>();
        for (int i = 0; i < 20; i++) {
            numbers.add(orderNumberGenerator.generate());
        }

        assertThat(numbers).hasSize(20);
    }
}
