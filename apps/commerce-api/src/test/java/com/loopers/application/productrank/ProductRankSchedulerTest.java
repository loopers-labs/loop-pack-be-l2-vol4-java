package com.loopers.application.productrank;

import com.loopers.domain.productrank.ProductRankRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProductRankSchedulerTest {

    @DisplayName("tick 은 읽기모델 재집계를 리포지토리에 위임한다")
    @Test
    void tick_delegates_rebuild_to_repository() {
        ProductRankRepository repository = Mockito.mock(ProductRankRepository.class);
        ProductRankScheduler scheduler = new ProductRankScheduler(repository);

        scheduler.tick();

        Mockito.verify(repository).rebuildFromSource();
    }
}
