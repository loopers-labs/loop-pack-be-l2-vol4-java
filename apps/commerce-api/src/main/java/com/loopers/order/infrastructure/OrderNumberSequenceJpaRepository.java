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
     * 증분에 걸린 행 X-lock 이 커밋까지 유지돼 동시 채번을 직렬화하므로, 같은 트랜잭션의 currentValue 는
     * 자기 증분값을 정확히 읽는다(명시적 비관 락 불필요).
     *
     * date 를 LocalDate 가 아니라 ISO 문자열(yyyy-MM-dd)로 바인딩한다 — LocalDate 바인딩은
     * JVM(Asia/Seoul)과 hibernate.jdbc.time_zone(UTC)이 어긋날 때 하루 밀린 날짜 행을 때려,
     * 주문번호 접두사와 다른 날짜의 카운터가 증가하며 중복 채번을 일으켰다(부하 테스트 general log 로 실증).
     */
    @Modifying
    @Query(value = """
        INSERT INTO order_number_sequences (order_date, last_seq)
        VALUES (:date, 1)
        ON DUPLICATE KEY UPDATE last_seq = last_seq + 1
        """, nativeQuery = true)
    void incrementOrCreate(@Param("date") String date);

    @Query(value = "SELECT last_seq FROM order_number_sequences WHERE order_date = :date", nativeQuery = true)
    long currentValue(@Param("date") String date);
}
