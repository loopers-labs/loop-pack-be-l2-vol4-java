package com.loopers.tddstudy.infrastructure.product;

import com.loopers.tddstudy.domain.product.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByBrandId(Long brandId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findWithLockById(Long id);

    // 브랜드 필터 + 좋아요 순
    List<Product> findAllByBrandIdOrderByLikeCountDesc(Long brandId);

    // 전체 + 좋아요 순
    List<Product> findAllByOrderByLikeCountDesc();

    List<Product> findTop10ByOrderByLikeCountDesc();

}
