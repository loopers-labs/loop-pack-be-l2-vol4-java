package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    @Query("SELECT p FROM ProductModel p " +
        "WHERE p.deletedAt IS NULL " +
        "AND (:brandId IS NULL OR p.brandId = :brandId) " +
        "ORDER BY p.createdAt DESC")
    List<ProductModel> findAllByLatest(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p FROM ProductModel p " +
        "WHERE p.deletedAt IS NULL " +
        "AND (:brandId IS NULL OR p.brandId = :brandId) " +
        "ORDER BY p.price ASC")
    List<ProductModel> findAllByPriceAsc(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p FROM ProductModel p " +
        "WHERE p.deletedAt IS NULL " +
        "AND (:brandId IS NULL OR p.brandId = :brandId) " +
        "ORDER BY p.likeCount DESC")
    List<ProductModel> findAllByLikesDesc(@Param("brandId") Long brandId, Pageable pageable);

    /** 좋아요 등록 시 조건부 원자 증가. 좋아요 INSERT 가 실제 성공한 경우에만 호출된다. */
    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    int increaseLikeCount(@Param("id") Long id);

    /** 좋아요 취소 시 조건부 원자 감소. 음수 방지(like_count > 0). 실제 삭제된 경우에만 호출된다. */
    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    int decreaseLikeCount(@Param("id") Long id);

    /**
     * 모든 상품의 like_count 를 실제 likes 집계로 재계산(drift 보정).
     *
     * <p>평소엔 등록/취소 시 원자 UPDATE 로 실시간 동기화되지만, 동기화 경로 버그나 DB 직접 조작으로
     * 누적될 수 있는 오차를 주기적으로 리셋한다. 관리용 수동 호출 전제.
     *
     * @return 갱신된 상품 행 수
     */
    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = "
        + "(SELECT COUNT(l) FROM com.loopers.domain.like.LikeModel l WHERE l.productId = p.id)")
    int resyncAllLikeCounts();

    @Query("SELECT p FROM ProductModel p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    List<ProductModel> findAllByBrandId(@Param("brandId") Long brandId);

    @Query("SELECT p FROM ProductModel p WHERE p.id IN :ids AND p.deletedAt IS NULL")
    List<ProductModel> findAllByIdInAndDeletedAtIsNull(@Param("ids") List<Long> ids);

    /**
     * 브랜드에 속한 미삭제 상품을 단일 UPDATE 쿼리로 soft delete.
     * deleted_at + status 를 동시에 변경하여 BrandApplicationService의 N+1(상품별 save 루프)을 제거한다.
     */
    @Modifying
    @Query(value = "UPDATE products SET deleted_at = NOW(), status = 'DELETED' " +
        "WHERE brand_id = :brandId AND deleted_at IS NULL",
        nativeQuery = true)
    void softDeleteAllByBrandId(@Param("brandId") Long brandId);
}
