package com.loopers.infrastructure.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

/**
 * product_metrics 접근 리포지토리. commerce-api 는 이 테이블의 <b>차원 컬럼</b>(brand_id/price/deleted_at)과
 * 행 라이프사이클을 소유한다. 측정값 컬럼(like/sales/view_count)은 commerce-streamer 가 이벤트 집계로 쓰므로
 * 여기서는 <b>절대 건드리지 않는다</b>(2-writer 컬럼 분리). 그래서 JPA dirty-checking 전체행 UPDATE 대신
 * 차원 컬럼만 겨냥한 명시적 쓰기({@code @Modifying})로만 갱신한다.
 */
public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsEntity, Long> {

    /**
     * 상품 생성 시 read model 행을 만든다. 측정값은 0으로 시작(이후 streamer 이벤트 집계로 누적).
     * 측정값 컬럼을 건드리지 않도록 dirty-checking(save/merge) 대신 명시적 INSERT 를 쓴다.
     */
    @Modifying
    @Query(value = "insert into product_metrics "
            + "(product_id, brand_id, price, like_count, sales_count, view_count, created_at, updated_at) "
            + "values (:productId, :brandId, :price, 0, 0, 0, now(), now())", nativeQuery = true)
    void insertDimensions(@Param("productId") Long productId,
                          @Param("brandId") Long brandId,
                          @Param("price") Long price);

    /**
     * 차원 컬럼(price, deleted_at)만 갱신한다. brand_id 는 불변이라 갱신하지 않는다.
     * 측정값 컬럼은 SET 절에 없으므로 streamer 가 누적한 값이 보존된다(clobber 없음).
     * 행이 없으면(백필 전) 영향 행 0 = no-op — reconcile/백필이 채운다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductMetricsEntity m set m.price = :price, m.deletedAt = :deletedAt, m.updatedAt = :now "
            + "where m.productId = :productId")
    int updateDimensions(@Param("productId") Long productId,
                         @Param("price") Long price,
                         @Param("deletedAt") ZonedDateTime deletedAt,
                         @Param("now") ZonedDateTime now);

    // --- 읽기(리스팅): 단일 테이블 정렬/필터/페이지. Sort 는 엔티티 프로퍼티명(likeCount/price/productId)으로 주어진다. ---
    List<ProductMetricsEntity> findByDeletedAtIsNull(Pageable pageable);

    List<ProductMetricsEntity> findByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    /** 좋아요 수 batch 조회용(활성/비활성 무관 — 호출측이 이미 활성 id 만 넘긴다). */
    List<ProductMetricsEntity> findByProductIdIn(Collection<Long> productIds);
}
