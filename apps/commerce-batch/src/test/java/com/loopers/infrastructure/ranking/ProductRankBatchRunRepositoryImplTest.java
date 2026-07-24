package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.BatchRunStatus;
import com.loopers.domain.ranking.ProductRankBatchRun;
import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductRankBatchRunRepositoryImplTest {

    @Test
    @DisplayName("배치 실행 이력을 RUNNING 상태로 저장한다.")
    void create_ShouldSaveRunningBatchRun() {
        ProductRankBatchRunJpaRepository jpaRepository = mock(ProductRankBatchRunJpaRepository.class);
        ProductRankBatchRunRepositoryImpl repository = new ProductRankBatchRunRepositoryImpl(jpaRepository);
        when(jpaRepository.save(any())).thenAnswer(invocation -> {
            ProductRankBatchRunJpaEntity entity = invocation.getArgument(0);
            return new ProductRankBatchRunJpaEntity(
                1L,
                entity.getPeriod(),
                entity.getRankStartDate(),
                entity.getRankEndDate(),
                entity.getStatus()
            );
        });

        ProductRankBatchRun batchRun = repository.create(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26)
        );

        assertThat(batchRun.getId()).isEqualTo(1L);
        assertThat(batchRun.getStatus()).isEqualTo(BatchRunStatus.RUNNING);
        verify(jpaRepository).save(any(ProductRankBatchRunJpaEntity.class));
    }

    @Test
    @DisplayName("저장된 배치 실행 이력을 도메인으로 조회한다.")
    void findById_ShouldReturnDomainBatchRun() {
        ProductRankBatchRunJpaRepository jpaRepository = mock(ProductRankBatchRunJpaRepository.class);
        ProductRankBatchRunRepositoryImpl repository = new ProductRankBatchRunRepositoryImpl(jpaRepository);
        when(jpaRepository.findById(1L)).thenReturn(Optional.of(new ProductRankBatchRunJpaEntity(
            1L,
            RankingPeriod.MONTHLY,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            BatchRunStatus.COMPLETED
        )));

        Optional<ProductRankBatchRun> batchRun = repository.findById(1L);

        assertThat(batchRun).isPresent();
        assertThat(batchRun.get().getPeriod()).isEqualTo(RankingPeriod.MONTHLY);
        assertThat(batchRun.get().getStatus()).isEqualTo(BatchRunStatus.COMPLETED);
    }

    @Test
    @DisplayName("배치 실행 이력을 완료 또는 실패 상태로 변경한다.")
    void markCompletedAndFailed_ShouldUpdateStatus() {
        ProductRankBatchRunJpaRepository jpaRepository = mock(ProductRankBatchRunJpaRepository.class);
        ProductRankBatchRunRepositoryImpl repository = new ProductRankBatchRunRepositoryImpl(jpaRepository);

        repository.markCompleted(1L);
        repository.markFailed(2L);

        verify(jpaRepository).updateStatus(1L, BatchRunStatus.COMPLETED);
        verify(jpaRepository).updateStatus(2L, BatchRunStatus.FAILED);
    }
}
