package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByIdAndDeletedAtIsNull(Long id);
    List<Brand> findAllByDeletedAtIsNull();
    List<Brand> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
    boolean existsByIdAndDeletedAtIsNull(Long id);
    boolean existsByName(String name);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Brand b
        SET b.deletedAt = CURRENT_TIMESTAMP, b.updatedAt = CURRENT_TIMESTAMP
        WHERE b.id = :id AND b.deletedAt IS NULL
        """)
    int softDeleteById(@Param("id") Long id);
}
