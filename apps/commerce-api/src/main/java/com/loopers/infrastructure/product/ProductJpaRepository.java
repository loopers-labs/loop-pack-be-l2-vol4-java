package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    /**
     * 단일 UPDATE 문으로 DB 가 행 단위 직렬화를 보장하므로 read-modify-write 방식의 lost update 가 발생하지
     * 않는다. 외부 트랜잭션이 있으면 참여하고, 없으면 새로 시작한다.
     */
    @Transactional
    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query(
        "UPDATE ProductModel p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1"
            + " ELSE 0 END WHERE p.id = :id")
    int decrementLikeCount(@Param("id") Long id);
}
