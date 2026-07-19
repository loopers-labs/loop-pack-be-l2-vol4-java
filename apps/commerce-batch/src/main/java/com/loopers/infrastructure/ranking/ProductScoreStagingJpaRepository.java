package com.loopers.infrastructure.ranking;

import com.loopers.batch.domain.ranking.ProductScoreStaging;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 중간 테이블 접근. writer 의 saveAll, clearStep 의 deleteAllInBatch(전체 비우기)는 모두 상속 메서드로 충분해 별도 선언이 없다.
 */
public interface ProductScoreStagingJpaRepository extends JpaRepository<ProductScoreStaging, Long> {
}
