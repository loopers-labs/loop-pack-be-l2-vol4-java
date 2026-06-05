package com.loopers.order.infrastructure;

import com.loopers.order.domain.OrderNumberSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface OrderNumberSequenceJpaRepository extends JpaRepository<OrderNumberSequence, LocalDate> {

    /**
     * 해당 날짜의 채번을 원자적으로 증가시킨다. 행이 없으면 1 로 생성하고, 있으면 1 증가시킨다.
     * DB 가 증분을 직렬화하므로 애플리케이션의 read-modify-write 와 명시적 비관 락이 필요 없다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO order_number_sequences (order_date, last_seq)
        VALUES (:date, 1)
        ON DUPLICATE KEY UPDATE last_seq = last_seq + 1
        """, nativeQuery = true)
    void incrementOrCreate(@Param("date") LocalDate date);

    @Query(value = "SELECT last_seq FROM order_number_sequences WHERE order_date = :date", nativeQuery = true)
    long currentValue(@Param("date") LocalDate date);
}
