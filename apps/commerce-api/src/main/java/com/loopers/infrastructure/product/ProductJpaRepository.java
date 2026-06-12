package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 원자적 조건부 재고 차감.
     * 읽기-검사-쓰기를 단일 UPDATE 문으로 처리해 동시 주문 간 lost update(초과판매)를 차단한다.
     * {@code stock >= :quantity} 조건이 충족되지 않으면 영향받은 행이 0이 되어 차감이 실패한 것으로 판단한다.
     *
     * @return 갱신된 행 수 (1이면 차감 성공, 0이면 재고 부족 또는 미존재)
     */
    @Modifying
    @Query("UPDATE ProductModel p SET p.stock = p.stock - :quantity " +
           "WHERE p.id = :id AND p.deletedAt IS NULL AND p.stock >= :quantity")
    int deductStock(@Param("id") Long id, @Param("quantity") int quantity);
}
