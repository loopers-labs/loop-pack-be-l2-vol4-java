package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    /**
     * 주문 경로 전용 비관적 락 조회. ORDER BY id 로 잠금 순서를 전역 고정해 데드락(순환 대기)을 예방한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    /**
     * 좋아요 수 원자적 증감. 읽기-계산-쓰기를 DB 한 문장으로 처리해 Lost Update 가 성립할 틈이 없다.
     */
    @Modifying
    @Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void increaseLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Product p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    void decreaseLikeCount(@Param("id") Long id);
}
